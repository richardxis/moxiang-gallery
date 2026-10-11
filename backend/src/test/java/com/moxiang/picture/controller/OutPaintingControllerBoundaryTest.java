package com.moxiang.picture.controller;

import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutPaintingControllerBoundaryTest {
    @Test void anonymousCreationCannotReachService() {
        UserService users = mock(UserService.class); OutPaintingService service = mock(OutPaintingService.class);
        MockHttpServletRequest http = new MockHttpServletRequest(); when(users.getLoginUser(http)).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        assertThrows(BusinessException.class, () -> new OutPaintingController(service, users).create(new OutPaintingRequest(), http)); verifyNoInteractions(service);
    }
    @Test void anonymousQueryCannotReachService() {
        UserService users = mock(UserService.class); OutPaintingService service = mock(OutPaintingService.class);
        MockHttpServletRequest http = new MockHttpServletRequest(); when(users.getLoginUser(http)).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        assertThrows(BusinessException.class, () -> new OutPaintingController(service, users).query("task", http)); verifyNoInteractions(service);
    }
    @Test void passesServerSessionUserToService() {
        UserService users = mock(UserService.class); OutPaintingService service = mock(OutPaintingService.class);
        MockHttpServletRequest http = new MockHttpServletRequest(); User user = new User(); user.setId(7L); when(users.getLoginUser(http)).thenReturn(user);
        new OutPaintingController(service, users).query("task", http); verify(service).query("task", user);
    }
}
