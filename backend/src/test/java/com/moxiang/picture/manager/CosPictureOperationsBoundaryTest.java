package com.moxiang.picture.manager;

import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosPictureOperationsBoundaryTest {
    @Test void preservesDirectoryAndDoesNotOverwriteOriginalWebp() {
        PicOperations operations = CosManager.buildPictureOperations("public/123/photo.webp", "test-bucket", 4096);
        assertEquals(2, operations.getRules().size());
        assertEquals("/public/123/photo_compressed.webp", operations.getRules().get(0).getFileId());
        assertEquals("/public/123/photo_thumbnail.webp", operations.getRules().get(1).getFileId());
        assertNotEquals("/public/123/photo.webp", operations.getRules().get(0).getFileId());
    }
    @Test void smallPicturesOnlyRequestCompression() {
        PicOperations operations = CosManager.buildPictureOperations("public/1/photo.jpg", "test", 2048);
        assertEquals(1, operations.getRules().size());
        assertEquals("imageMogr2/format/webp", operations.getRules().get(0).getRule());
    }
}
