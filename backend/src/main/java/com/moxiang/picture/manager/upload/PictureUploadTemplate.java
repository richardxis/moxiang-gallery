package com.moxiang.picture.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.moxiang.picture.config.CosClientConfig;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import com.moxiang.picture.manager.CosManager;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.UUID;

/** 原型模板流程：校验、落临时文件、COS 上传、元数据、清理。 */
@Slf4j
public abstract class PictureUploadTemplate {
    public static final long MAX_BYTES = 2 * 1024 * 1024L;
    @Resource private CosClientConfig cosClientConfig;
    @Resource private CosManager cosManager;

    public final UploadPictureResult uploadPicture(Object source, String prefix) {
        return uploadPictureToBase(source,prefix,null);
    }
    public final UploadPictureResult uploadPictureToBase(Object source,String prefix,String plannedBase) {
        validate(source);
        String pictureName = FileUtil.mainName(originalName(source));
        if (pictureName == null || pictureName.trim().isEmpty()) pictureName = "picture";
        if (pictureName.length() > 128) throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片名称过长");
        File temporary = null;
        try {
            temporary = Files.createTempFile("moxiang-picture-", ".tmp").toFile();
            writeSource(source, temporary);
            if (temporary.length() == 0 || temporary.length() > MAX_BYTES) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不能为空且不能超过 2MB");
            }
            String format = PictureFileValidation.detectFormat(temporary);
            if(plannedBase!=null&&(!plannedBase.startsWith(prefix+"/")||!com.moxiang.picture.service.PictureObjectCleanupService.safeKey(plannedBase+".png")))throw new BusinessException(ErrorCode.PARAMS_ERROR,"上传路径无效");
            String key = (plannedBase==null?prefix + "/" + LocalDate.now() + "_" + UUID.randomUUID():plannedBase) + "." + format;
            PutObjectResult uploaded = cosManager.putPictureObject(key, temporary);
            if (uploaded == null || uploaded.getCiUploadResult() == null
                    || uploaded.getCiUploadResult().getOriginalInfo() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "对象存储未返回图片元数据");
            }
            ImageInfo info = uploaded.getCiUploadResult().getOriginalInfo().getImageInfo();
            if (info == null || info.getWidth() <= 0 || info.getHeight() <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片元数据无效");
            }
            UploadPictureResult result = new UploadPictureResult();
            result.setStorageBucket(cosClientConfig.getBucket());
            result.setOriginalKey(key);
            result.setUrl(cosClientConfig.getHost().replaceAll("/+$", "") + "/" + key);
            result.setPicName(pictureName);
            result.setPicWidth(info.getWidth());
            result.setPicHeight(info.getHeight());
            result.setPicScale(NumberUtil.round(info.getWidth() * 1.0 / info.getHeight(), 2).doubleValue());
            result.setPicSize(temporary.length());
            result.setPicFormat(info.getFormat());
            result.setPicColor(info.getAve());
            result.setThumbnailUrl(result.getUrl());
            applyProcessedResult(result, uploaded, key);
            if(result.getCompressedKey()==null)result.setCompressedKey(key);
            if(result.getThumbnailKey()==null)result.setThumbnailKey(result.getCompressedKey());
            return result;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败，请检查存储配置或稍后重试");
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary.toPath()); }
                catch (Exception e) { log.warn("临时图片清理失败", e); }
            }
        }
    }

    private void applyProcessedResult(UploadPictureResult result, PutObjectResult uploaded, String key) {
        ProcessResults processed = uploaded.getCiUploadResult().getProcessResults();
        if (processed == null || processed.getObjectList() == null) return;
        CIObject compressed = null, thumbnail = null;
        for (CIObject object : processed.getObjectList()) {
            if (object == null || object.getKey() == null) continue;
            String actualKey = object.getKey().replaceFirst("^/+", "");
            if (CosManager.compressedKey(key).equals(actualKey)) compressed = object;
            if (CosManager.thumbnailKey(key).equals(actualKey)) thumbnail = object;
        }
        if (compressed != null && compressed.getWidth() > 0 && compressed.getHeight() > 0
                && compressed.getSize() != null && compressed.getSize().longValue() > 0) {
            result.setUrl(publicUrl(compressed.getKey()));
            result.setCompressedKey(compressed.getKey().replaceFirst("^/+",""));
            result.setPicWidth(compressed.getWidth());
            result.setPicHeight(compressed.getHeight());
            result.setPicScale(NumberUtil.round(compressed.getWidth() * 1.0 / compressed.getHeight(), 2).doubleValue());
            result.setPicSize(compressed.getSize().longValue());
            result.setPicFormat(compressed.getFormat());
            result.setThumbnailUrl(result.getUrl());
        }
        if (thumbnail != null) { result.setThumbnailUrl(publicUrl(thumbnail.getKey()));result.setThumbnailKey(thumbnail.getKey().replaceFirst("^/+","")); }
    }
    private String publicUrl(String key) {
        return cosClientConfig.getHost().replaceAll("/+$", "") + "/" + key.replaceFirst("^/+", "");
    }

    protected abstract void validate(Object source);
    protected abstract String originalName(Object source);
    protected abstract void writeSource(Object source, File destination) throws Exception;
}
