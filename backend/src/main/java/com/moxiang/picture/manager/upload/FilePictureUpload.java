package com.moxiang.picture.manager.upload;

import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

@Service
public class FilePictureUpload extends PictureUploadTemplate {
    @Override protected void validate(Object source) {
        if (!(source instanceof MultipartFile)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        MultipartFile file = (MultipartFile) source;
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不能为空且不能超过 2MB");
        }
        String name = originalName(source);
        String suffix = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("jpg", "jpeg", "png", "webp").contains(suffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片扩展名不支持");
        }
    }
    @Override protected String originalName(Object source) {
        String name = ((MultipartFile) source).getOriginalFilename();
        return name == null ? "picture" : name;
    }
    @Override protected void writeSource(Object source, File destination) throws Exception {
        ((MultipartFile) source).transferTo(destination);
    }
}
