package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.search.ImageSearchGateway;
import com.moxiang.picture.mapper.PictureMapper;
import com.moxiang.picture.model.dto.picture.PictureSearchRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureSearchVO.ColorMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PictureDiscoveryBoundaryTest {
    PictureMapper pictures;SpaceService spaces;UserService users;ImageSearchGateway gateway;PictureDiscoveryService service;User actor;PictureSearchRequest request;
    @BeforeEach void setup(){pictures=mock(PictureMapper.class);spaces=mock(SpaceService.class);users=mock(UserService.class);gateway=mock(ImageSearchGateway.class);service=new PictureDiscoveryService(pictures,spaces,users,gateway);actor=new User();actor.setId(7L);when(users.getById(7L)).thenReturn(actor);request=new PictureSearchRequest();request.setPictureId(1L);request.setPicColor("#FF0000");}
    Picture picture(long id,String color){Picture picture=new Picture();picture.setId(id);picture.setPicColor(color);picture.setTags("[]");picture.setReviewStatus(1);picture.setUrl("https://images.example/"+id+".png");return picture;}
    @Test void externalSearchRejectsMissingLogin(){assertThrows(BusinessException.class,()->service.byPicture(request,null));verifyNoInteractions(gateway);}
    @Test void externalSearchRejectsPrivateImageEvenForItsOwner(){Picture source=picture(1,"#FF0000");source.setSpaceId(10L);when(pictures.selectById(1L)).thenReturn(source);assertEquals(40101,assertThrows(BusinessException.class,()->service.byPicture(request,actor)).getCode());verifyNoInteractions(gateway);}
    @Test void externalSearchRejectsUnreviewedPublicImage(){Picture source=picture(1,"#FF0000");source.setReviewStatus(0);when(pictures.selectById(1L)).thenReturn(source);assertThrows(BusinessException.class,()->service.byPicture(request,actor));verifyNoInteractions(gateway);}
    @Test void publicApprovedImageCallsGatewayWithStoredUrl(){Picture source=picture(1,"#FF0000");when(pictures.selectById(1L)).thenReturn(source);service.byPicture(request,actor);verify(gateway).search(source.getUrl(),12);}
    @Test void externalSearchPrefersOriginalPngOverDisplayWebp(){Picture source=picture(1,"#FF0000");source.setUrl("https://images.example/public/7/a_compressed.webp");source.setOriginalKey("public/7/a.png");when(pictures.selectById(1L)).thenReturn(source);service.byPicture(request,actor);verify(gateway).search("https://images.example/public/7/a.png",12);}
    @Test void invalidOriginalKeyCannotEscapePublicMaterial(){Picture source=picture(1,"#FF0000");source.setOriginalKey("public/7/../private.png");assertEquals(source.getUrl(),PictureDiscoveryService.searchSourceUrl(source));}
    @Test void legacyWebpUsesCosPngConversionWithoutChangingStoredImage(){Picture source=picture(1,"#FF0000");source.setUrl("https://images.example/public/7/old.webp");assertEquals(source.getUrl()+"?imageMogr2/format/png",PictureDiscoveryService.searchSourceUrl(source));assertEquals("https://images.example/public/7/old.webp",source.getUrl());}
    @Test void scopePermissionIsCheckedBeforeColorRead(){request.setSpaceId(10L);when(spaces.authorized(10L,actor)).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));assertThrows(BusinessException.class,()->service.byColor(request,actor));verifyNoInteractions(pictures);}
    @Test void colorSortSkipsMalformedValuesAndBreaksTiesById(){
        List<Picture> rows=Arrays.asList(picture(3,"#00FF00"),picture(2,"0xFF0000"),picture(1,"#FF0000"),picture(4,"bad"));
        when(pictures.selectList(any())).thenReturn(rows,rows);List<ColorMatch> result=service.byColor(request,actor);
        assertEquals(3,result.size());assertEquals(1L,result.get(0).getId());assertEquals(2L,result.get(1).getId());assertEquals(1.0,result.get(0).getSimilarity());
    }
    @Test void thresholdAndTopNAreApplied(){request.setTopN(1);request.setMinSimilarity(0.99);List<Picture> rows=Arrays.asList(picture(1,"#FF0000"),picture(2,"#00FF00"));when(pictures.selectList(any())).thenReturn(rows,rows);assertEquals(1,service.byColor(request,actor).size());}
    @Test void nanThresholdRejected(){request.setMinSimilarity(Double.NaN);assertThrows(BusinessException.class,()->service.byColor(request,actor));verifyNoInteractions(pictures);}
    @Test void topNOutOfBoundsRejected(){request.setTopN(51);assertThrows(BusinessException.class,()->service.byColor(request,actor));}
    @Test void emptyCandidatesDoNotLoadAllPictures(){when(pictures.selectList(any())).thenReturn(Collections.emptyList());assertTrue(service.byColor(request,actor).isEmpty());verify(pictures,times(1)).selectList(any());}
    @Test void candidateOverflowIsExplicitRatherThanSilentTruncation(){when(pictures.selectList(any())).thenReturn(Collections.nCopies(10001,picture(1,"#FF0000")));assertThrows(BusinessException.class,()->service.byColor(request,actor));}
    @Test void finalReadReappliesPublicReviewAndScope(){List<Picture> rows=Collections.singletonList(picture(1,"#FF0000"));when(pictures.selectList(any())).thenReturn(rows,Collections.emptyList());assertTrue(service.byColor(request,actor).isEmpty());ArgumentCaptor<QueryWrapper<Picture>> query=ArgumentCaptor.forClass(QueryWrapper.class);verify(pictures,times(2)).selectList(query.capture());for(QueryWrapper<Picture> wrapper:query.getAllValues()){assertTrue(wrapper.getSqlSegment().contains("spaceId IS NULL"));assertTrue(wrapper.getParamNameValuePairs().containsValue(1));}}
    @Test void removedAccountCannotSearchColor(){when(users.getById(7L)).thenReturn(null);assertThrows(BusinessException.class,()->service.byColor(request,actor));verifyNoInteractions(pictures);}
}
