package com.moxiang.picture.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.moxiang.picture.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = buildPictureOperations(key, cosClientConfig.getBucket(), file.length());
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    public static PicOperations buildPictureOperations(String key, String bucket, long size) {
        PicOperations operations = new PicOperations();
        operations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        PicOperations.Rule compressed = new PicOperations.Rule();
        compressed.setBucket(bucket);
        // fileId 以 / 开头表示桶内绝对路径；保留用户目录，避免不同目录重名。
        compressed.setFileId("/" + compressedKey(key));
        compressed.setRule("imageMogr2/format/webp");
        rules.add(compressed);
        // 原型实际阈值为 2KB，虽然其注释写 20KB；这里明确使用实际条件。
        if (size > 2 * 1024) {
            PicOperations.Rule thumbnail = new PicOperations.Rule();
            thumbnail.setBucket(bucket);
            thumbnail.setFileId("/" + thumbnailKey(key));
            thumbnail.setRule("imageMogr2/thumbnail/256x256>/format/webp");
            rules.add(thumbnail);
        }
        operations.setRules(rules);
        return operations;
    }
    public static String compressedKey(String key) { return baseKey(key) + "_compressed.webp"; }
    public static String thumbnailKey(String key) { return baseKey(key) + "_thumbnail.webp"; }
    private static String baseKey(String key) {
        String normalized = key.replaceFirst("^/+", "");
        int dot = normalized.lastIndexOf('.');
        return dot > normalized.lastIndexOf('/') ? normalized.substring(0, dot) : normalized;
    }


}
