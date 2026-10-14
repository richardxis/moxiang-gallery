package com.moxiang.picture.controller;

import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.dto.picture.PictureBatchEditRequest;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.model.vo.PictureSearchVO.*;
import com.moxiang.picture.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PictureDiscoveryControllerBoundaryTest {
    MockMvc mvc;PictureDiscoveryService discovery;PictureBatchEditService batch;UserService users;User actor;
    @BeforeEach void setup(){discovery=mock(PictureDiscoveryService.class);batch=mock(PictureBatchEditService.class);users=mock(UserService.class);actor=new User();actor.setId(7L);when(users.getLoginUser(any())).thenReturn(actor);mvc=MockMvcBuilders.standaloneSetup(new PictureDiscoveryController(discovery,batch,users)).setControllerAdvice(new GlobalExceptionHandler()).build();}
    @Test void pictureRouteReturnsExplicitManualStatus()throws Exception{ExternalResult result=new ExternalResult();result.setStatus("OPEN_PROVIDER");result.setPageUrl("https://graph.baidu.com/s");when(discovery.byPicture(any(),eq(actor))).thenReturn(result);mvc.perform(post("/picture/search/picture").contentType(MediaType.APPLICATION_JSON).content("{\"pictureId\":\"10\"}")).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.status").value("OPEN_PROVIDER"));}
    @Test void colorRouteKeepsSimilarityInResponse()throws Exception{ColorMatch result=new ColorMatch();result.setId(10L);result.setSimilarity(0.9);when(discovery.byColor(any(),eq(actor))).thenReturn(Collections.singletonList(result));mvc.perform(post("/picture/search/color").contentType(MediaType.APPLICATION_JSON).content("{\"picColor\":\"#FF0000\"}")).andExpect(jsonPath("$.data[0].similarity").value(0.9));}
    @Test void batchRouteConvertsStringIdsAndRevisionKeysToLongs()throws Exception{when(batch.edit(any(),eq(actor))).thenReturn(new BatchResult());mvc.perform(post("/picture/edit/batch").contentType(MediaType.APPLICATION_JSON).content("{\"pictureIdList\":[\"9007199254740993\"],\"spaceId\":\"10\",\"tags\":[],\"expectedRevisions\":{\"9007199254740993\":\"2\"}}")).andExpect(jsonPath("$.code").value(0));ArgumentCaptor<PictureBatchEditRequest> request=ArgumentCaptor.forClass(PictureBatchEditRequest.class);verify(batch).edit(request.capture(),eq(actor));assertEquals(9007199254740993L,request.getValue().getPictureIdList().get(0));assertEquals(2L,request.getValue().getExpectedRevisions().get(9007199254740993L));assertTrue(request.getValue().getTags().isEmpty());}
    @ParameterizedTest @ValueSource(strings={"search/picture","search/color","edit/batch"})void allRoutesRequireLogin(String route)throws Exception{when(users.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));mvc.perform(post("/picture/"+route).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(jsonPath("$.code").value(40100));verifyNoInteractions(discovery,batch);}
}
