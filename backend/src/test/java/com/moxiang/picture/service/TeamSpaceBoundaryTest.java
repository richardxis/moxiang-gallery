package com.moxiang.picture.service;

import com.moxiang.picture.manager.auth.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.dto.space.SpaceCreateRequest;
import com.moxiang.picture.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TeamSpaceBoundaryTest {
    SpaceMapper spaces; PictureMapper pictures; SpaceUserMapper members; SpaceRbac rbac; UserService users;
    PlatformTransactionManager manager; SpaceService service; User user; Space space;
    @BeforeEach void setup() {
        spaces = mock(SpaceMapper.class); pictures = mock(PictureMapper.class); members = mock(SpaceUserMapper.class); rbac = mock(SpaceRbac.class); users = mock(UserService.class); manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new SpaceService(spaces, pictures, users, new TransactionTemplate(manager));
        ReflectionTestUtils.setField(service, "members", members); ReflectionTestUtils.setField(service, "rbac", rbac);
        user = new User(); user.setId(7L); space = new Space(); space.setId(10L); space.setUserId(99L); space.setSpaceType(1);
        when(spaces.selectById(10L)).thenReturn(space); when(spaces.selectForUpdate(10L)).thenReturn(space);
        when(spaces.insert(any(Space.class))).thenAnswer(call -> { ((Space) call.getArgument(0)).setId(10L); return 1; });
        when(members.insert(any(SpaceUser.class))).thenReturn(1);
    }
    @Test void teamCreationAddsOwnerAsAdministratorInSameTransaction() {
        SpaceCreateRequest request = new SpaceCreateRequest(); request.setSpaceType(1); Space result = service.create(request, user);
        assertEquals(1, result.getSpaceType()); verify(members).insert(argThat((SpaceUser m) -> m.getSpaceId().equals(10L) && m.getUserId().equals(7L) && m.getSpaceRole().equals("admin"))); verify(manager).commit(any());
    }
    @Test void failedOwnerMembershipRollsBackTeamCreation() {
        when(members.insert(any(SpaceUser.class))).thenReturn(0); SpaceCreateRequest request = new SpaceCreateRequest(); request.setSpaceType(1);
        assertThrows(BusinessException.class, () -> service.create(request, user)); verify(manager).rollback(any()); verify(manager, never()).commit(any());
    }
    @Test void viewIsNotSufficientForUpload() { doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)).when(rbac).check(10L, user, SpacePermissions.UPLOAD); assertSame(space, service.authorized(10L, user)); assertThrows(BusinessException.class, () -> service.checkUpload(10L, user)); }
    @Test void revokedEditPermissionBlocksFinalMetadataWrite() {
        Picture update = new Picture(); update.setId(9L); doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)).when(rbac).check(10L, user, SpacePermissions.EDIT);
        assertThrows(BusinessException.class, () -> service.editMetadata(update, 10L, user)); verify(pictures, never()).updateById(any(Picture.class)); verify(manager).rollback(any());
    }
    @Test void metadataWriteLocksSpaceBeforeCheckingPermission() {
        Picture update = new Picture(); update.setId(9L); update.setSpaceId(10L); when(pictures.selectForUpdate(9L)).thenReturn(update); when(pictures.updateById(update)).thenReturn(1);
        service.editMetadata(update, 10L, user); org.mockito.InOrder order = inOrder(spaces, rbac, pictures);
        order.verify(spaces).selectForUpdate(10L); order.verify(spaces).selectById(10L); order.verify(rbac).check(10L, user, SpacePermissions.EDIT); order.verify(pictures).selectForUpdate(9L); order.verify(pictures).updateById(update);
    }
    @Test void invalidTeamTypeRejected() { SpaceCreateRequest request = new SpaceCreateRequest(); request.setSpaceType(2); assertThrows(BusinessException.class, () -> service.create(request, user)); verifyNoInteractions(manager); }
}
