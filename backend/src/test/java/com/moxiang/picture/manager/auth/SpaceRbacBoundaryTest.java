package com.moxiang.picture.manager.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.spring.SaBeanInject;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.service.UserService;
import com.moxiang.picture.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpaceRbacBoundaryTest {
    SpaceMapper spaces; SpaceUserMapper members; UserService users; SpacePermissionProvider provider;
    SpaceRbac rbac; User user; Space space; SpaceUser member; StpInterface previous;
    @BeforeEach void setup() {
        previous = SaManager.getStpInterface(); spaces = mock(SpaceMapper.class); members = mock(SpaceUserMapper.class); users = mock(UserService.class);
        provider = new SpacePermissionProvider(spaces, members, users); SaManager.setStpInterface(provider); rbac = new SpaceRbac();
        user = new User(); user.setId(8L); space = new Space(); space.setId(10L); space.setUserId(7L); space.setSpaceType(1);
        member = new SpaceUser(); member.setUserId(8L); member.setSpaceId(10L); member.setSpaceRole("viewer");
        when(spaces.selectById(10L)).thenReturn(space); when(users.getById(8L)).thenReturn(user); when(members.findMembership(10L, 8L)).thenReturn(member);
    }
    @AfterEach void restore() { SaManager.setStpInterface(previous); }
    @Test void viewerMayViewButCannotWriteOrManage() {
        assertDoesNotThrow(() -> rbac.check(10L, user, SpacePermissions.VIEW));
        for (String permission : new String[]{SpacePermissions.UPLOAD, SpacePermissions.EDIT, SpacePermissions.DELETE, SpacePermissions.MANAGE})
            assertThrows(BusinessException.class, () -> rbac.check(10L, user, permission));
    }
    @Test void editorMayWriteAndDeleteButCannotManageMembers() {
        member.setSpaceRole("editor");
        for (String permission : new String[]{SpacePermissions.VIEW, SpacePermissions.UPLOAD, SpacePermissions.EDIT, SpacePermissions.DELETE})
            assertDoesNotThrow(() -> rbac.check(10L, user, permission));
        assertThrows(BusinessException.class, () -> rbac.check(10L, user, SpacePermissions.MANAGE));
    }
    @Test void administratorMayManageMembers() { member.setSpaceRole("admin"); assertEquals(5, rbac.permissions(10L, user).size()); }
    @Test void ownerRetainsAuthorityWithoutMembershipRow() { space.setUserId(8L); when(members.findMembership(10L, 8L)).thenReturn(null); assertEquals(5, rbac.permissions(10L, user).size()); }
    @Test void platformAdministratorHasAuthority() { when(users.isAdmin(user)).thenReturn(true); assertEquals(5, rbac.permissions(10L, user).size()); }
    @Test void strangerAndInvalidRoleHaveNoAuthority() {
        member.setSpaceRole("root"); assertTrue(rbac.permissions(10L, user).isEmpty());
        when(members.findMembership(10L, 8L)).thenReturn(null); assertThrows(BusinessException.class, () -> rbac.check(10L, user, SpacePermissions.VIEW));
    }
    @Test void membershipRevocationIsReadOnNextCheck() {
        assertDoesNotThrow(() -> rbac.check(10L, user, SpacePermissions.VIEW));
        when(members.findMembership(10L, 8L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> rbac.check(10L, user, SpacePermissions.VIEW));
    }
    @Test void oneSpaceMembershipDoesNotGrantAnotherSpace() { assertThrows(BusinessException.class, () -> rbac.check(11L, user, SpacePermissions.VIEW)); }
    @Test void missingDeletedUserAndUnknownSpaceFailClosed() {
        when(users.getById(8L)).thenReturn(null); assertTrue(rbac.permissions(10L, user).isEmpty());
        when(users.getById(8L)).thenReturn(user); space.setSpaceType(2); assertTrue(rbac.permissions(10L, user).isEmpty());
    }
    @Test void personalSpaceMembershipDoesNotGrantAccess() { space.setSpaceType(0); member.setSpaceRole("admin"); assertTrue(rbac.permissions(10L, user).isEmpty()); }
    @Test void invalidScopeAndLoginTypeAreRejectedBeforeDatabaseAccess() {
        for (String key : new String[]{"", "10", "10:-8", "0:8", "10:8:7", "9223372036854775808:8"}) assertTrue(provider.getPermissionList(key, "space").isEmpty());
        assertTrue(provider.getPermissionList("10:8", "login").isEmpty()); verifyNoInteractions(spaces, members, users);
    }
    @Test void anonymousUserCannotAskForPermissions() { assertThrows(BusinessException.class, () -> rbac.permissions(10L, null)); }
    @Test void springInjectsProviderIntoActualSaTokenRuntime() {
        SaManager.setStpInterface(previous);
        new ApplicationContextRunner().withBean(StpInterface.class, () -> provider).withUserConfiguration(SaBeanInject.class)
                .run(context -> { assertNull(context.getStartupFailure()); assertSame(provider, SaManager.getStpInterface()); assertDoesNotThrow(() -> rbac.check(10L, user, SpacePermissions.VIEW)); });
    }
}
