package com.moxiang.picture.manager.websocket;
import com.moxiang.picture.constant.UserConstant;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaborationAccessBoundaryTest {
    UserService users; PictureService pictures; SpaceService spaces; CollaborationAccess access; MockHttpSession session; User user; Picture picture; Space space; CollaborationAccess.Binding binding;
    @BeforeEach void setup() {
        users=mock(UserService.class); pictures=mock(PictureService.class); spaces=mock(SpaceService.class); access=new CollaborationAccess(users,pictures,spaces);
        user=new User(); user.setId(7L); session=new MockHttpSession(); session.setAttribute(UserConstant.USER_LOGIN_STATE,user); picture=new Picture(); picture.setId(10L); picture.setSpaceId(20L); space=new Space(); space.setSpaceType(1);
        when(users.getById(7L)).thenReturn(user); when(pictures.getById(10L)).thenReturn(picture); when(spaces.requirePermission(20L,user,SpacePermissions.EDIT)).thenReturn(space); binding=new CollaborationAccess.Binding(session,7L,10L,20L);
    }
    @Test void teamEditorAuthorized() { assertDoesNotThrow(()->access.verify(binding)); }
    @Test void logoutImmediatelyDeniesFurtherMessages() { session.removeAttribute(UserConstant.USER_LOGIN_STATE); assertThrows(BusinessException.class,()->access.verify(binding)); }
    @Test void expiredHttpSessionDenied() { session.invalidate(); assertThrows(BusinessException.class,()->access.verify(binding)); }
    @Test void identityChangeInHttpSessionDenied() { User other=new User(); other.setId(8L); session.setAttribute(UserConstant.USER_LOGIN_STATE,other); assertThrows(BusinessException.class,()->access.verify(binding)); }
    @Test void removedUserDenied() { when(users.getById(7L)).thenReturn(null); assertThrows(BusinessException.class,()->access.verify(binding)); }
    @Test void deletedOrMovedPictureDenied() { when(pictures.getById(10L)).thenReturn(null); assertThrows(BusinessException.class,()->access.verify(binding)); when(pictures.getById(10L)).thenReturn(picture); picture.setSpaceId(21L); assertThrows(BusinessException.class,()->access.verify(binding)); }
    @Test void viewerAndRevokedMemberDenied() { when(spaces.requirePermission(20L,user,SpacePermissions.EDIT)).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)); assertThrows(BusinessException.class,()->access.verify(binding)); }
    @Test void personalAndPublicPicturesDenied() { space.setSpaceType(0); assertThrows(BusinessException.class,()->access.verify(binding)); picture.setSpaceId(null); assertThrows(BusinessException.class,()->access.bind(session,user,10L)); }
    @Test void missingSessionBindingDenied() { assertThrows(BusinessException.class,()->access.bind(null,user,10L)); }
}
