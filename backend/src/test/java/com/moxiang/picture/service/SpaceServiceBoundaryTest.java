package com.moxiang.picture.service;

import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.space.SpaceCreateRequest;
import com.moxiang.picture.model.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SpaceServiceBoundaryTest {
    SpaceMapper spaces; PictureMapper pictures; UserService users; PlatformTransactionManager manager;
    SpaceService service; User user; Space space;
    @BeforeEach void setup() {
        spaces = mock(SpaceMapper.class); pictures = mock(PictureMapper.class); users = mock(UserService.class); manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new SpaceService(spaces, pictures, users, new TransactionTemplate(manager));
        user = new User(); user.setId(7L);
        space = new Space(); space.setId(10L); space.setUserId(7L); space.setSpaceType(0);
        space.setMaxCount(100L); space.setMaxSize(100L * 1024 * 1024); space.setTotalCount(1L); space.setTotalSize(1000L);
        when(spaces.selectById(10L)).thenReturn(space); when(spaces.insert(any(Space.class))).thenReturn(1);
        when(spaces.selectForUpdate(10L)).thenReturn(space);
        when(pictures.selectById(9L)).thenReturn(picture(1000));
        when(spaces.adjustUsage(anyLong(), anyLong(), anyLong())).thenReturn(1);
        when(pictures.insert(any(Picture.class))).thenReturn(1); when(pictures.updateById(any(Picture.class))).thenReturn(1);
        when(pictures.deleteById(anyLong())).thenReturn(1);
    }
    Picture picture(long size) {
        Picture picture = new Picture(); picture.setId(9L); picture.setSpaceId(10L); picture.setUserId(7L); picture.setPicSize(size); picture.setUrl("https://pictures.example/" + size); return picture;
    }
    @Test void creationAssignsServerOwnerAndCommonQuota() {
        SpaceCreateRequest request = new SpaceCreateRequest(); request.setSpaceName(" 测试空间 ");
        Space created = service.create(request, user);
        assertEquals(7L, created.getUserId()); assertEquals("测试空间", created.getSpaceName()); assertEquals(100L, created.getMaxCount());
        assertEquals(100L * 1024 * 1024, created.getMaxSize()); assertEquals(0, created.getSpaceType());
    }
    @Test void duplicateOwnerConstraintHasBusinessError() {
        when(spaces.insert(any(Space.class))).thenThrow(new DuplicateKeyException("test"));
        assertThrows(BusinessException.class, () -> service.create(new SpaceCreateRequest(), user));
    }
    @Test void loginRequired() { assertThrows(BusinessException.class, () -> service.create(new SpaceCreateRequest(), null)); verifyNoInteractions(spaces); }
    @Test void rejectsBlankOrLongName() {
        SpaceCreateRequest request = new SpaceCreateRequest(); request.setSpaceName(" "); assertThrows(BusinessException.class, () -> service.create(request, user));
        request.setSpaceName(new String(new char[129]).replace('\0', 'a')); assertThrows(BusinessException.class, () -> service.create(request, user));
    }
    @Test void ownerCanReadSpace() { assertSame(space, service.authorized(10L, user)); }
    @Test void strangerCannotReadSpace() { user.setId(8L); assertThrows(BusinessException.class, () -> service.authorized(10L, user)); }
    @Test void administratorCanReadPersonalSpace() { user.setId(8L); when(users.isAdmin(user)).thenReturn(true); assertSame(space, service.authorized(10L, user)); }
    @Test void unknownSpaceTypeFailsClosedEvenForAdministrator() { space.setSpaceType(2); when(users.isAdmin(user)).thenReturn(true); assertThrows(BusinessException.class, () -> service.authorized(10L, user)); }
    @Test void preliminaryFullQuotaCheckBlocksUpload() { space.setTotalCount(100L); assertThrows(BusinessException.class, () -> service.checkUpload(10L, user)); }
    @Test void newUploadAddsSizeAndOnePictureThenCommits() {
        service.saveUploaded(picture(2000), null, user); verify(spaces).adjustUsage(10L, 2000, 1); verify(pictures).insert(any(Picture.class)); verify(manager).commit(any());
    }
    @Test void replacementOnlyAdjustsSizeDelta() {
        Picture previous = picture(1000); when(pictures.selectForUpdate(9L)).thenReturn(previous);
        service.saveUploaded(picture(1500), previous, user); verify(spaces).adjustUsage(10L, 500, 0); verify(pictures).updateById(any(Picture.class));
    }
    @Test void staleReplacementIsRejectedBeforeQuotaWrite() {
        when(pictures.selectForUpdate(9L)).thenReturn(picture(1200));
        assertThrows(BusinessException.class, () -> service.saveUploaded(picture(1500), picture(1000), user));
        verify(spaces, never()).adjustUsage(anyLong(), anyLong(), anyLong()); verify(manager).rollback(any());
    }
    @Test void conditionalQuotaFailurePreventsPictureInsert() {
        when(spaces.adjustUsage(anyLong(), anyLong(), anyLong())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.saveUploaded(picture(2000), null, user)); verify(pictures, never()).insert(any(Picture.class)); verify(manager).rollback(any());
    }
    @Test void failedInsertRollsBackQuotaTransaction() {
        when(pictures.insert(any(Picture.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.saveUploaded(picture(2000), null, user)); verify(manager).rollback(any()); verify(manager, never()).commit(any());
    }
    @Test void deleteReleasesQuotaInTransaction() {
        when(pictures.selectForUpdate(9L)).thenReturn(picture(1000)); service.deletePicture(9L, user);
        verify(spaces).adjustUsage(10L, -1000, -1); verify(manager).commit(any());
    }
    @Test void quotaErrorRollsBackDelete() {
        when(pictures.selectForUpdate(9L)).thenReturn(picture(1000)); when(spaces.adjustUsage(10L, -1000, -1)).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.deletePicture(9L, user)); verify(manager).rollback(any());
    }
}
