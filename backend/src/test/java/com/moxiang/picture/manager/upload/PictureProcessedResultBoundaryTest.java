package com.moxiang.picture.manager.upload;

import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.*;
import com.moxiang.picture.config.CosClientConfig;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.manager.CosManager;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PictureProcessedResultBoundaryTest {
    private FilePictureUpload uploader;
    private CosManager storage;
    private final AtomicReference<File> temporary = new AtomicReference<>();
    @BeforeEach void setup() {
        uploader = new FilePictureUpload(); storage = mock(CosManager.class);
        CosClientConfig config = new CosClientConfig(); config.setHost("https://pictures.example/");
        ReflectionTestUtils.setField(uploader, "cosClientConfig", config);
        ReflectionTestUtils.setField(uploader, "cosManager", storage);
    }
    private MockMultipartFile file() {
        return new MockMultipartFile("file", "sample.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
    }
    private PutObjectResult original() {
        ImageInfo info = new ImageInfo(); info.setWidth(100); info.setHeight(200); info.setFormat("png"); info.setAve("0xff00ff");
        OriginalInfo original = new OriginalInfo(); original.setImageInfo(info);
        CIUploadResult ci = new CIUploadResult(); ci.setOriginalInfo(original);
        PutObjectResult result = new PutObjectResult(); result.setCiUploadResult(ci); return result;
    }
    @Test void usesProcessedKeysInsteadOfObjectListOrderAndCleansTemporaryFile() {
        when(storage.putPictureObject(anyString(), any(File.class))).thenAnswer(call -> {
            String key = call.getArgument(0); File temp = call.getArgument(1); temporary.set(temp); assertTrue(temp.exists());
            PutObjectResult result = original();
            CIObject compressed = new CIObject(); compressed.setKey(CosManager.compressedKey(key));
            compressed.setWidth(50); compressed.setHeight(100); compressed.setSize(1000); compressed.setFormat("webp");
            CIObject thumbnail = new CIObject(); thumbnail.setKey(CosManager.thumbnailKey(key));
            ProcessResults processed = new ProcessResults(); processed.setObjectList(Arrays.asList(thumbnail, compressed));
            result.getCiUploadResult().setProcessResults(processed); return result;
        });
        UploadPictureResult result = uploader.uploadPicture(file(), "public/1");
        assertTrue(result.getUrl().endsWith("_compressed.webp")); assertTrue(result.getThumbnailUrl().endsWith("_thumbnail.webp"));
        assertEquals(50, result.getPicWidth()); assertEquals(Long.valueOf(1000), result.getPicSize());
        assertEquals("0xff00ff", result.getPicColor()); assertFalse(temporary.get().exists());
        assertFalse(result.getUrl().contains("example//"));
    }
    @Test void missingProcessingResultsFallBackToOriginalAndThumbnailFallback() {
        when(storage.putPictureObject(anyString(), any(File.class))).thenReturn(original());
        UploadPictureResult result = uploader.uploadPicture(file(), "public/1");
        assertTrue(result.getUrl().endsWith(".png")); assertEquals(result.getUrl(), result.getThumbnailUrl());
        assertEquals(100, result.getPicWidth());
    }
    @Test void missingMetadataFailsAndStillCleansTemporaryFile() {
        when(storage.putPictureObject(anyString(), any(File.class))).thenAnswer(call -> {
            temporary.set(call.getArgument(1)); return new PutObjectResult();
        });
        assertThrows(BusinessException.class, () -> uploader.uploadPicture(file(), "public/1"));
        assertFalse(temporary.get().exists());
    }
}
