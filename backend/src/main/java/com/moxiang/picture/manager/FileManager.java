package com.moxiang.picture.manager;

import com.moxiang.picture.manager.upload.FilePictureUpload;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;

/** 兼容早期调用方；实际上传统一交给模板，避免保留两套实现。 */
@Service
@Deprecated
public class FileManager {
    @Resource private FilePictureUpload filePictureUpload;

    public UploadPictureResult uploadPicture(MultipartFile file, String prefix) {
        return filePictureUpload.uploadPicture(file, prefix);
    }
}
