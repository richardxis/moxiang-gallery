package com.moxiang.picture.controller;

import com.moxiang.picture.common.DeleteRequest;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import com.moxiang.picture.model.dto.picture.PictureUpdateRequest;
import com.moxiang.picture.model.entity.Picture;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.model.vo.PictureVO;
import com.moxiang.picture.service.PictureService;
import com.moxiang.picture.service.UserService;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PictureControllerBoundaryTest {
    private PictureController controller;
    private PictureService pictures;
    private UserService users;
    private MockHttpServletRequest request;
    @BeforeEach void setup() {
        controller = new PictureController();
        pictures = mock(PictureService.class);
        users = mock(UserService.class);
        request = new MockHttpServletRequest();
        ReflectionTestUtils.setField(controller, "pictureService", pictures);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "publicPictureCache", mock(PublicPictureCache.class));
        ReflectionTestUtils.setField(controller, "spaceService", mock(com.moxiang.picture.service.SpaceService.class));
    }
    @Test void rejectsNullDeleteIdBeforeDatabaseAccess() {
        assertThrows(BusinessException.class, () -> controller.deletePicture(new DeleteRequest(), request));
        verifyNoInteractions(pictures, users);
    }
    @Test void rejectsInvalidPaginationBeforeDatabaseAccess() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setPageSize(0);
        assertThrows(BusinessException.class, () -> controller.listPictureVOByPage(query, request));
        verifyNoInteractions(pictures);
    }
    @Test void preventsNonOwnerFromReadingUnapprovedPicture() {
        Picture picture = new Picture();
        picture.setUserId(1L);
        picture.setReviewStatus(0);
        User viewer = new User();
        viewer.setId(2L);
        when(pictures.getById(1L)).thenReturn(picture);
        when(users.getLoginUser(request)).thenReturn(viewer);
        assertThrows(BusinessException.class, () -> controller.getPictureVOById(1L, request));
        verify(pictures, never()).getPictureVO(any(), any());
    }
    @Test void permitsAnonymousReadOfApprovedPublicPicture() {
        Picture picture = new Picture();
        picture.setReviewStatus(1);
        when(pictures.getById(1L)).thenReturn(picture);
        when(pictures.getPictureVO(picture, request)).thenReturn(new PictureVO());
        assertEquals(0, controller.getPictureVOById(1L, request).getCode());
        verifyNoInteractions(users);
    }
    @Test void rejectsSpacePictureBeforePublicDetailResponse() {
        Picture picture = new Picture(); picture.setSpaceId(10L); picture.setReviewStatus(1);
        when(pictures.getById(1L)).thenReturn(picture);
        com.moxiang.picture.service.SpaceService spaces = (com.moxiang.picture.service.SpaceService) ReflectionTestUtils.getField(controller, "spaceService");
        when(spaces.authorized(eq(10L), isNull())).thenThrow(new BusinessException(com.moxiang.picture.exception.ErrorCode.NOT_LOGIN_ERROR));
        assertThrows(BusinessException.class, () -> controller.getPictureVOById(1L, request));
        verify(pictures, never()).getPictureVO(any(), any());
    }
    @Test void authorizedSpaceDetailCanBeReadWithoutPublicReview() {
        Picture picture = new Picture(); picture.setSpaceId(10L); picture.setReviewStatus(0);
        User owner = new User(); owner.setId(7L);
        when(users.getLoginUser(request)).thenReturn(owner); when(pictures.getById(1L)).thenReturn(picture);
        when(pictures.getPictureVO(picture, request)).thenReturn(new PictureVO());
        assertEquals(0, controller.getPictureVOById(1L, request).getCode());
        com.moxiang.picture.service.SpaceService spaces = (com.moxiang.picture.service.SpaceService) ReflectionTestUtils.getField(controller, "spaceService");
        verify(spaces).authorized(10L, owner);
    }
    @Test void persistsReviewFieldsOnTheActualUpdateObject() {
        PictureUpdateRequest update = new PictureUpdateRequest();
        update.setId(1L);
        when(pictures.getById(1L)).thenReturn(new Picture());
        User admin = new User();
        when(users.getLoginUser(request)).thenReturn(admin);
        doAnswer(call -> { ((Picture) call.getArgument(0)).setReviewStatus(1); return null; })
                .when(pictures).fillReviewParams(any(), eq(admin));
        when(pictures.editMetadata(any(),nullable(Long.class),eq(admin))).thenAnswer(call -> {
            assertEquals(Integer.valueOf(1), ((Picture) call.getArgument(0)).getReviewStatus());
            return true;
        });
        assertEquals(0, controller.updatePicture(update, request).getCode());
    }
    @Test void spacePaginationChecksAuthAndDoesNotForcePublicReview() {
        PictureQueryRequest query = new PictureQueryRequest(); query.setSpaceId(10L); query.setPageSize(12); query.setNullSpaceId(true); query.setReviewStatus(2);
        User owner = new User(); owner.setId(7L); when(users.getLoginUser(request)).thenReturn(owner);
        when(pictures.getQueryWrapper(any())).thenAnswer(call -> {
            PictureQueryRequest effective = call.getArgument(0); assertEquals(10L, effective.getSpaceId()); assertFalse(effective.isNullSpaceId()); assertNull(effective.getReviewStatus());
            return new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Picture>();
        });
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Picture> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(pictures.page(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any())).thenReturn(page);
        when(pictures.getPictureVOPage(page, request)).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<PictureVO>());
        assertEquals(0, controller.listPictureVOByPage(query, request).getCode());
        com.moxiang.picture.service.SpaceService spaces = (com.moxiang.picture.service.SpaceService) ReflectionTestUtils.getField(controller, "spaceService");
        verify(spaces).authorized(10L, owner);
    }
    @Test void spaceDeletionUsesQuotaAwareService() {
        Picture picture = new Picture(); picture.setId(9L); picture.setSpaceId(10L); when(pictures.getById(9L)).thenReturn(picture);
        User owner = new User(); owner.setId(7L); when(users.getLoginUser(request)).thenReturn(owner);
        DeleteRequest delete = new DeleteRequest(); delete.setId(9L); assertEquals(0, controller.deletePicture(delete, request).getCode());
        com.moxiang.picture.service.SpaceService spaces = (com.moxiang.picture.service.SpaceService) ReflectionTestUtils.getField(controller, "spaceService");
        verify(pictures).deleteVersioned(eq(9L),isNull(),eq(owner)); verify(pictures, never()).removeById(anyLong());
    }
}
