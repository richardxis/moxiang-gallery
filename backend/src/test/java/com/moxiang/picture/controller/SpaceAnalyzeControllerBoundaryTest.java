package com.moxiang.picture.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaceAnalyzeControllerBoundaryTest {
    MockMvc mvc;
    UserService users;
    @BeforeEach void setup() {
        users = mock(UserService.class); PictureMapper pictures = mock(PictureMapper.class); SpaceMapper spaces = mock(SpaceMapper.class);
        User admin = new User(); admin.setId(7L); when(users.getLoginUser(any())).thenReturn(admin); when(users.isAdmin(admin)).thenReturn(true);
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(new HashMap<>()));
        when(spaces.selectList(any())).thenReturn(Collections.emptyList());
        SpaceAnalyzeService service = new SpaceAnalyzeService(pictures, spaces, mock(SpaceService.class), users, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new SpaceAnalyzeController(service, users)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @ParameterizedTest @ValueSource(strings = {"usage", "category", "tag", "size", "user", "rank"})
    void sixEndpointsReturnSuccessEnvelope(String endpoint) throws Exception {
        mvc.perform(post("/space/analyze/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queryPublic\":true,\"timeDimension\":\"day\",\"topN\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
    @Test void missingSessionIsBusinessUnauthorized() throws Exception {
        when(users.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        mvc.perform(post("/space/analyze/usage").contentType(MediaType.APPLICATION_JSON).content("{\"queryAll\":true}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
    }
    @Test void ambiguousScopeIsRejectedByHttpApi() throws Exception {
        mvc.perform(post("/space/analyze/category").contentType(MediaType.APPLICATION_JSON)
                .content("{\"queryAll\":true,\"spaceId\":10}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAMS_ERROR.getCode()));
    }
}
