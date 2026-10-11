package com.moxiang.picture.manager.upload;

import com.moxiang.picture.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PictureUploadBoundaryTest {
    @TempDir Path directory;

    @Test void rejectsEmptyAndOversizedLocalFiles() {
        FilePictureUpload uploader = new FilePictureUpload();
        assertThrows(BusinessException.class, () -> uploader.validate(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0])));
        assertThrows(BusinessException.class, () -> uploader.validate(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[(int)PictureUploadTemplate.MAX_BYTES + 1])));
    }
    @Test void acceptsUppercaseExtensionWithinSizeLimit() {
        assertDoesNotThrow(() -> new FilePictureUpload().validate(new MockMultipartFile("file", "A.JPG", "image/jpeg", new byte[]{1})));
    }
    @Test void rejectsDisguisedImageContent() throws Exception {
        Path file = directory.resolve("fake.png");
        Files.write(file, "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> PictureFileValidation.detectFormat(file.toFile()));
    }
    @Test void recognizesPngSignature() throws Exception {
        Path file = directory.resolve("picture");
        Files.write(file, new byte[]{(byte)137,80,78,71,13,10,26,10});
        assertEquals("png", PictureFileValidation.detectFormat(file.toFile()));
    }
    @Test void rejectsPrivateAndLocalAddresses() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "192.168.1.2", "169.254.169.254", "100.64.0.1", "::1", "fc00::1"}) {
            assertFalse(UrlPictureUpload.isPublicAddress(InetAddress.getByName(ip)), ip);
        }
    }
    @Test void rejectsUnconfiguredHostsAndUnsupportedSchemesWithoutNetwork() {
        UrlPictureUpload uploader = new UrlPictureUpload();
        ReflectionTestUtils.setField(uploader, "allowedHosts", "");
        assertThrows(BusinessException.class, () -> uploader.validateUrl("https://example.com/a.png"));
        assertThrows(BusinessException.class, () -> uploader.validateUrl("file:///a.png"));
        assertThrows(BusinessException.class, () -> uploader.validateUrl("http://user:pass@example.com/a.png"));
    }
    @Test void enforcesRealDownloadLimitWithoutContentLength() {
        InputStream input = new ByteArrayInputStream(new byte[(int)PictureUploadTemplate.MAX_BYTES + 1]);
        assertThrows(BusinessException.class, () -> UrlPictureUpload.copyBounded(input, new ByteArrayOutputStream(), Long.MAX_VALUE));
    }
    @Test void enforcesDownloadDeadline() {
        assertThrows(BusinessException.class, () -> UrlPictureUpload.copyBounded(new ByteArrayInputStream(new byte[]{1}), new ByteArrayOutputStream(), 0));
    }
}
