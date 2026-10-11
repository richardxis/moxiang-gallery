package com.moxiang.picture.service;

import com.moxiang.picture.mapper.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.dto.space.SpaceMemberRequest;
import com.moxiang.picture.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SpaceMemberBoundaryTest {
    SpaceService spaces; SpaceMapper mapper; SpaceUserMapper members; UserService users; PlatformTransactionManager manager;
    SpaceMemberService service; User actor; Space space; SpaceMemberRequest request; SpaceUser existing;
    @BeforeEach void setup() {
        spaces = mock(SpaceService.class); mapper = mock(SpaceMapper.class); members = mock(SpaceUserMapper.class); users = mock(UserService.class); manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new SpaceMemberService(spaces, mapper, members, users, new TransactionTemplate(manager));
        actor = new User(); actor.setId(7L); space = new Space(); space.setId(10L); space.setUserId(7L); space.setSpaceType(1);
        when(mapper.selectForUpdate(10L)).thenReturn(space); when(spaces.requirePermission(10L, actor, SpacePermissions.MANAGE)).thenReturn(space);
        request = new SpaceMemberRequest(); request.setSpaceId(10L); request.setUserId(8L); request.setSpaceRole("viewer");
        existing = new SpaceUser(); existing.setId(50L); existing.setSpaceId(10L); existing.setUserId(8L); existing.setSpaceRole("viewer");
        when(users.getById(8L)).thenReturn(new User()); when(members.insert(any(SpaceUser.class))).thenReturn(1);
        when(members.updateById(any(SpaceUser.class))).thenReturn(1); when(members.deleteById(50L)).thenReturn(1);
    }
    @Test void ownerCanAddRegisteredViewer() { service.add(request, actor); verify(members).insert(argThat((SpaceUser m) -> m.getUserId().equals(8L) && m.getSpaceRole().equals("viewer"))); verify(manager).commit(any()); }
    @Test void duplicateMembershipRejected() { when(members.findMembership(10L, 8L)).thenReturn(existing); assertThrows(BusinessException.class, () -> service.add(request, actor)); verify(members, never()).insert(any(SpaceUser.class)); }
    @Test void nonexistentTargetUserRejected() { when(users.getById(8L)).thenReturn(null); assertThrows(BusinessException.class, () -> service.add(request, actor)); verify(manager).rollback(any()); }
    @Test void invalidRoleRejectedBeforeTransaction() { request.setSpaceRole("root"); assertThrows(BusinessException.class, () -> service.add(request, actor)); verifyNoInteractions(manager, mapper); }
    @Test void ownerCannotBeRemovedOrDowngraded() { request.setUserId(7L); assertThrows(BusinessException.class, () -> service.remove(request, actor)); assertThrows(BusinessException.class, () -> service.change(request, actor)); }
    @Test void ownerCanAssignAdministrator() { request.setSpaceRole("admin"); service.add(request, actor); verify(members).insert(argThat((SpaceUser m) -> m.getSpaceRole().equals("admin"))); }
    @Test void delegatedAdministratorCannotAssignAdministrator() { space.setUserId(99L); request.setSpaceRole("admin"); assertThrows(BusinessException.class, () -> service.add(request, actor)); }
    @Test void delegatedAdministratorCannotChangeOrRemoveExistingAdministrator() { space.setUserId(99L); existing.setSpaceRole("admin"); when(members.findMembership(10L, 8L)).thenReturn(existing); assertThrows(BusinessException.class, () -> service.change(request, actor)); assertThrows(BusinessException.class, () -> service.remove(request, actor)); }
    @Test void delegatedAdministratorCanManageOrdinaryMember() { space.setUserId(99L); when(members.findMembership(10L, 8L)).thenReturn(existing); request.setSpaceRole("editor"); service.change(request, actor); verify(members).updateById(argThat((SpaceUser m) -> m.getSpaceRole().equals("editor"))); }
    @Test void removalPhysicallyDeletesMember() { when(members.findMembership(10L, 8L)).thenReturn(existing); service.remove(request, actor); verify(members).deleteById(50L); verify(manager).commit(any()); }
    @Test void missingMembershipCannotBeChanged() { assertThrows(BusinessException.class, () -> service.change(request, actor)); }
    @Test void permissionRevokedBeforeFinalMutationPreventsWrite() { when(spaces.requirePermission(10L, actor, SpacePermissions.MANAGE)).thenThrow(new BusinessException(com.moxiang.picture.exception.ErrorCode.NO_AUTH_ERROR)); assertThrows(BusinessException.class, () -> service.add(request, actor)); verify(members, never()).insert(any(SpaceUser.class)); verify(manager).rollback(any()); }
    @Test void failedInsertRollsBack() { when(members.insert(any(SpaceUser.class))).thenReturn(0); assertThrows(BusinessException.class, () -> service.add(request, actor)); verify(manager).rollback(any()); }
    @Test void spaceRowLockPrecedesPermissionAndMemberWrite() { service.add(request, actor); org.mockito.InOrder order = inOrder(mapper, spaces, members); order.verify(mapper).selectForUpdate(10L); order.verify(spaces).requirePermission(10L, actor, SpacePermissions.MANAGE); order.verify(members).findMembership(10L, 8L); order.verify(members).insert(any(SpaceUser.class)); }
    @Test void personalSpaceDoesNotSupportMembers() { space.setSpaceType(0); assertThrows(BusinessException.class, () -> service.add(request, actor)); }
    @Test void oversizedOrDeepPaginationRejected() { assertThrows(BusinessException.class, () -> service.list(10L, 10001, 20, actor)); assertThrows(BusinessException.class, () -> service.list(10L, 1, 51, actor)); }
}
