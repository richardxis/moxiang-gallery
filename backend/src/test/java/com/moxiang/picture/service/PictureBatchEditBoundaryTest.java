package com.moxiang.picture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.picture.*;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureSearchVO.BatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PictureBatchEditBoundaryTest {
    PictureMapper pictures;SpaceMapper rows;SpaceService spaces;UserService users;PictureWriteGate gate;PlatformTransactionManager manager;PublicPictureCache cache;PictureBatchEditService service;User actor;PictureBatchEditRequest request;
    @BeforeEach void setup(){
        pictures=mock(PictureMapper.class);rows=mock(SpaceMapper.class);spaces=mock(SpaceService.class);users=mock(UserService.class);gate=new PictureWriteGate();manager=mock(PlatformTransactionManager.class);cache=mock(PublicPictureCache.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());service=new PictureBatchEditService(pictures,rows,spaces,users,gate,new TransactionTemplate(manager),cache,new ObjectMapper());
        actor=new User();actor.setId(7L);when(users.getById(7L)).thenReturn(actor);when(rows.selectForUpdate(10L)).thenReturn(new Space());
        when(pictures.selectForUpdate(1L)).thenReturn(picture(1,10L));when(pictures.selectForUpdate(2L)).thenReturn(picture(2,10L));when(pictures.updateById(any(Picture.class))).thenReturn(1);
        request=new PictureBatchEditRequest();request.setSpaceId(10L);request.setPictureIdList(Arrays.asList(2L,1L));request.setExpectedRevisions(new HashMap<>());request.getExpectedRevisions().put(1L,3L);request.getExpectedRevisions().put(2L,3L);request.setCategory("素材");
    }
    Picture picture(long id,Long spaceId){Picture picture=new Picture();picture.setId(id);picture.setSpaceId(spaceId);picture.setRevision(3L);picture.setName("old"+id);return picture;}
    @Test void rejectsAnonymousBeforeAnyTransaction(){assertThrows(BusinessException.class,()->service.edit(request,null));verifyNoInteractions(manager);}
    @Test void rejectsEmptySelection(){request.setPictureIdList(Collections.emptyList());assertThrows(BusinessException.class,()->service.edit(request,actor));}
    @Test void rejectsMoreThanOneHundredInputIds(){request.setPictureIdList(Collections.nCopies(101,1L));assertThrows(BusinessException.class,()->service.edit(request,actor));}
    @Test void rejectsMissingRevisionEvenIfServerKnowsCurrentVersion(){request.getExpectedRevisions().remove(2L);assertThrows(BusinessException.class,()->service.edit(request,actor));verifyNoInteractions(manager);}
    @Test void rejectsUnexpectedRevisionMapKeys(){request.getExpectedRevisions().put(9L,3L);assertThrows(BusinessException.class,()->service.edit(request,actor));}
    @Test void staleMemberCannotWrite(){when(spaces.requirePermission(10L,actor,SpacePermissions.EDIT)).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));assertThrows(BusinessException.class,()->service.edit(request,actor));verify(pictures,never()).updateById(any(Picture.class));verify(manager).rollback(any());}
    @Test void rejectsMixedSpaceWithoutAnyPartialWrite(){when(pictures.selectForUpdate(2L)).thenReturn(picture(2,11L));assertThrows(BusinessException.class,()->service.edit(request,actor));verify(pictures,never()).updateById(any(Picture.class));verify(manager).rollback(any());}
    @Test void staleSecondPictureDoesNotModifyFirst(){request.getExpectedRevisions().put(2L,2L);assertEquals(40900,assertThrows(BusinessException.class,()->service.edit(request,actor)).getCode());verify(pictures,never()).updateById(any(Picture.class));}
    @Test void activeCollaborationBlocksBatchAndRollsBack(){gate.with(2L,()->{gate.register(2L,new PictureWriteGate.Fence(){public void ordinary(){throw new BusinessException(ErrorCode.CONFLICT_ERROR);}public PictureWriteGate.Snapshot validate(PictureSaveRequest r,User u,javax.servlet.http.HttpSession s){return null;}public void committed(Picture p,String id){}public void deleted(){}});return null;});assertThrows(BusinessException.class,()->service.edit(request,actor));verify(pictures,never()).updateById(any(Picture.class));verify(manager).rollback(any());}
    @Test void emptyCategoryAndTagsExplicitlyClearFieldsAndIncrementVersions(){request.setCategory("");request.setTags(Collections.emptyList());BatchResult result=service.edit(request,actor);assertEquals(2,result.getUpdatedCount());assertEquals(4L,result.getRevisions().get(1L));ArgumentCaptor<Picture> update=ArgumentCaptor.forClass(Picture.class);verify(pictures,times(2)).updateById(update.capture());for(Picture picture:update.getAllValues()){assertEquals("",picture.getCategory());assertEquals("[]",picture.getTags());}verify(manager).commit(any());verifyNoInteractions(cache);}
    @Test void namesFollowSelectionOrderNotLockOrder(){request.setNameRule("素材{序号}-{原名}");service.edit(request,actor);ArgumentCaptor<Picture> update=ArgumentCaptor.forClass(Picture.class);verify(pictures,times(2)).updateById(update.capture());assertEquals(2L,update.getAllValues().get(0).getId());assertEquals("素材1-old2",update.getAllValues().get(0).getName());assertEquals("素材2-old1",update.getAllValues().get(1).getName());}
    @Test void unknownNamePlaceholderIsRejected(){request.setNameRule("{执行脚本}");assertThrows(BusinessException.class,()->service.edit(request,actor));verifyNoInteractions(manager);}
    @Test void expandedNameLengthIsCheckedBeforeWriting(){request.setNameRule(String.join("",Collections.nCopies(120,"x"))+"{原名}");Picture picture=picture(1,10L);picture.setName("a-very-long-old-name");when(pictures.selectForUpdate(1L)).thenReturn(picture);assertThrows(BusinessException.class,()->service.edit(request,actor));verify(pictures,never()).updateById(any(Picture.class));}
    @Test void invalidTagsAreRejected(){request.setTags(Arrays.asList("A"," "));assertThrows(BusinessException.class,()->service.edit(request,actor));}
    @Test void duplicateIdsAreOnlyWrittenOnce(){request.setPictureIdList(Arrays.asList(2L,1L,2L));assertEquals(2,service.edit(request,actor).getUpdatedCount());verify(pictures,times(2)).updateById(any(Picture.class));}
    @Test void updateFailureRollsBackAndDoesNotInvalidateCache(){when(pictures.updateById(any(Picture.class))).thenReturn(1,0);assertThrows(BusinessException.class,()->service.edit(request,actor));verify(manager).rollback(any());verify(manager,never()).commit(any());verifyNoInteractions(cache);}
    @Test void publicBatchRequiresSystemAdministrator(){request.setSpaceId(null);assertThrows(BusinessException.class,()->service.edit(request,actor));verify(pictures,never()).updateById(any(Picture.class));}
    @Test void successfulPublicBatchInvalidatesOnceAfterCommit(){request.setSpaceId(null);when(users.isAdmin(actor)).thenReturn(true);when(pictures.selectForUpdate(1L)).thenReturn(picture(1,null));when(pictures.selectForUpdate(2L)).thenReturn(picture(2,null));service.edit(request,actor);org.mockito.InOrder order=inOrder(manager,cache);order.verify(manager).commit(any());order.verify(cache).invalidateAfterCommit();}
}
