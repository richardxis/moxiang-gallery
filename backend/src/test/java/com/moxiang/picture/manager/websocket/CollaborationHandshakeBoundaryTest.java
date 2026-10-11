package com.moxiang.picture.manager.websocket;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.mock.web.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CollaborationHandshakeBoundaryTest {
    UserService users; CollaborationAccess access; CollaborationHandshake handshake; MockHttpServletRequest request; MockHttpServletResponse response; Map<String,Object> attributes; User user;
    @BeforeEach void setup() {
        users=mock(UserService.class); access=mock(CollaborationAccess.class); handshake=new CollaborationHandshake(users,access,"http://localhost:5173");
        request=new MockHttpServletRequest(); request.addHeader("Origin","http://localhost:5173"); request.setParameter("pictureId","10"); request.setSession(new MockHttpSession()); response=new MockHttpServletResponse(); attributes=new HashMap<>(); user=new User(); user.setId(7L); when(users.getLoginUser(request)).thenReturn(user);
        when(access.bind(request.getSession(),user,10L)).thenReturn(new CollaborationAccess.Binding(request.getSession(),7L,10L,20L));
    }
    boolean attempt() { return handshake.beforeHandshake(new ServletServerHttpRequest(request),new ServletServerHttpResponse(response),null,attributes); }
    @Test void validLoginPermissionAndOriginBindServerScope() { assertTrue(attempt()); assertInstanceOf(CollaborationAccess.Binding.class,attributes.get(CollaborationHandshake.BINDING)); }
    @Test void unknownOriginRejectedBeforeLogin() { request.removeHeader("Origin"); request.addHeader("Origin","https://evil.example"); assertFalse(attempt()); assertEquals(403,response.getStatus()); verifyNoInteractions(users,access); }
    @Test void absentOriginRejected() { request.removeHeader("Origin"); assertFalse(attempt()); }
    @Test void wildcardConfigurationRejected() { assertThrows(IllegalArgumentException.class,()->new CollaborationHandshake(users,access,"*")); }
    @Test void nonServletRequestFailsClosed() { ServerHttpRequest other=mock(ServerHttpRequest.class); assertFalse(handshake.beforeHandshake(other,new ServletServerHttpResponse(response),null,attributes)); }
    @Test void missingOrInvalidIdRejected() { request.removeParameter("pictureId"); assertFalse(attempt()); request.setParameter("pictureId","-1"); assertFalse(attempt()); request.setParameter("pictureId","9223372036854775808"); assertFalse(attempt()); assertEquals(400,response.getStatus()); }
    @Test void unauthenticatedHandshakeRejected() { when(users.getLoginUser(request)).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR)); assertFalse(attempt()); assertEquals(401,response.getStatus()); }
    @Test void viewerPublicOrPersonalPictureRejected() { when(access.bind(any(),any(),anyLong())).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)); assertFalse(attempt()); assertEquals(403,response.getStatus()); }
    @Test void databaseFailureReturnsUnavailableNotSuccess() { when(access.bind(any(),any(),anyLong())).thenThrow(new IllegalStateException("simulated unavailable")); assertFalse(attempt()); assertEquals(503,response.getStatus()); }
}
