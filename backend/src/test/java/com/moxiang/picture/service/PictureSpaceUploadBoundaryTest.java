package com.moxiang.picture.service;

import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.manager.upload.FilePictureUpload;
import com.moxiang.picture.manager.CosManager;
import com.moxiang.picture.config.CosClientConfig;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.*;
import java.io.File;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import com.moxiang.picture.model.dto.picture.PictureUploadRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureVO;
import com.moxiang.picture.service.impl.PictureServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PictureSpaceUploadBoundaryTest {
    PictureServiceImpl service; SpaceService spaces; UserService users; PublicPictureCache cache; FilePictureUpload uploader;
    User user; MockMultipartFile file; PictureUploadRequest request; CosManager storage;
    PictureMutationService mutations;PictureAssetService assets;PictureObjectCleanupService cleanup;
    @BeforeEach void setup() {
        service = spy(new PictureServiceImpl()); spaces = mock(SpaceService.class); users = mock(UserService.class); cache = mock(PublicPictureCache.class); uploader = new FilePictureUpload(); storage = mock(CosManager.class);
        CosClientConfig config = new CosClientConfig(); config.setHost("https://pictures.example");
        ReflectionTestUtils.setField(uploader, "cosClientConfig", config); ReflectionTestUtils.setField(uploader, "cosManager", storage);
        ReflectionTestUtils.setField(service, "spaceService", spaces); ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "publicPictureCache", cache); ReflectionTestUtils.setField(service, "filePictureUpload", uploader);
        mutations=mock(PictureMutationService.class);assets=mock(PictureAssetService.class);cleanup=mock(PictureObjectCleanupService.class);
        ReflectionTestUtils.setField(service,"mutations",mutations);ReflectionTestUtils.setField(service,"assets",assets);ReflectionTestUtils.setField(service,"cleanup",cleanup);
        when(assets.prepare(anyString())).thenAnswer(call->{String prefix=call.getArgument(0);PictureCleanupTask task=new PictureCleanupTask();task.setId("test-task");return new PictureAssetService.Prepared(prefix,prefix+"/2026-10-09_00000000-0000-0000-0000-000000000007",task);});
        when(assets.upload(any(),any())).thenAnswer(call->{PictureAssetService.Prepared p=call.getArgument(1);return uploader.uploadPictureToBase(call.getArgument(0),p.prefix,p.root);});
        when(mutations.saveUpload(any(),nullable(Picture.class),nullable(Long.class),any(),anyString())).thenAnswer(call->{Picture fresh=call.getArgument(0);spaces.saveUploaded(fresh,call.getArgument(1),call.getArgument(3));return fresh;});
        user = new User(); user.setId(7L); file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
        request = new PictureUploadRequest(); request.setSpaceId(10L);request.setExpectedRevision(0L);
        ImageInfo info = new ImageInfo(); info.setWidth(100); info.setHeight(100); info.setFormat("png");
        OriginalInfo original = new OriginalInfo(); original.setImageInfo(info); CIUploadResult ci = new CIUploadResult(); ci.setOriginalInfo(original);
        PutObjectResult result = new PutObjectResult(); result.setCiUploadResult(ci);
        when(storage.putPictureObject(anyString(), any(File.class))).thenReturn(result);
    }
    @Test void spaceUploadUsesScopeAndBypassesPublicCache() {
        PictureVO result = service.uploadPicture(file, request, user); assertEquals(10L, result.getSpaceId());
        verify(storage).putPictureObject(startsWith("space/10/"), any(File.class)); verify(spaces).saveUploaded(argThat(picture -> Integer.valueOf(1).equals(picture.getReviewStatus())), isNull(), eq(user)); verifyNoInteractions(cache);
    }
    @Test void unauthorizedSpaceDoesNotUploadToCos() {
        when(spaces.requirePermission(10L, user, com.moxiang.picture.manager.auth.SpacePermissions.UPLOAD)).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));
        assertThrows(BusinessException.class, () -> service.uploadPicture(file, request, user)); verifyNoInteractions(storage);
    }
    @Test void replacementRetainsPersistedSpaceWithoutRequestSpaceId() {
        Picture old = new Picture(); old.setId(9L); old.setSpaceId(10L); old.setUserId(7L); doReturn(old).when(service).getById(9L);
        request.setId(9L); request.setSpaceId(null); PictureVO result = service.uploadPicture(file, request, user);
        assertEquals(10L, result.getSpaceId()); verify(spaces).saveUploaded(any(Picture.class), eq(old), eq(user)); verify(storage).putPictureObject(startsWith("space/10/"), any(File.class));
    }
    @Test void publicPictureCannotBeMovedByForgedSpaceId() {
        Picture old = new Picture(); old.setId(9L); old.setUserId(7L); doReturn(old).when(service).getById(9L); request.setId(9L);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file, request, user)); verifyNoInteractions(storage, spaces);
    }
    @Test void quotaPersistenceFailureIsNotReportedAsUploadSuccess() {
        doThrow(new BusinessException(ErrorCode.OPERATION_ERROR)).when(spaces).saveUploaded(any(Picture.class), isNull(), eq(user));
        assertThrows(BusinessException.class, () -> service.uploadPicture(file, request, user)); verifyNoInteractions(cache);
    }
    @Test void spaceOwnerCanReplacePictureUploadedByAdministrator() {
        Picture old = new Picture(); old.setId(9L); old.setSpaceId(10L); old.setUserId(8L); doReturn(old).when(service).getById(9L);
        request.setId(9L); assertEquals(10L, service.uploadPicture(file, request, user).getSpaceId());
        verify(spaces).requirePermission(10L, user, com.moxiang.picture.manager.auth.SpacePermissions.EDIT);
    }
}
