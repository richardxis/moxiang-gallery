package com.moxiang.picture.service;
import com.moxiang.picture.config.CosClientConfig;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.upload.*;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import com.moxiang.picture.model.entity.*;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class PictureAssetService {
    private final FilePictureUpload file;private final UrlPictureUpload url;private final PictureObjectCleanupService cleanup;private final COSClient cos;private final CosClientConfig config;
    public PictureAssetService(FilePictureUpload file,UrlPictureUpload url,PictureObjectCleanupService cleanup,COSClient cos,CosClientConfig config){this.file=file;this.url=url;this.cleanup=cleanup;this.cos=cos;this.config=config;}
    public static final class Prepared{public final String prefix,root;public final PictureCleanupTask task;Prepared(String prefix,String root,PictureCleanupTask task){this.prefix=prefix;this.root=root;this.task=task;}}
    public Prepared prepare(String prefix){String root=prefix+"/"+LocalDate.now()+"_"+UUID.randomUUID();return new Prepared(prefix,root,cleanup.hold(root));}
    public UploadPictureResult upload(Object source,Prepared p){return (source instanceof String?url:file).uploadPictureToBase(source,p.prefix,p.root);}
    public void copyKeys(Picture picture,UploadPictureResult result){picture.setStorageBucket(result.getStorageBucket());picture.setOriginalKey(result.getOriginalKey());picture.setCompressedKey(result.getCompressedKey());picture.setThumbnailKey(result.getThumbnailKey());}
    /** Server-owned object lookup; no caller-provided URLs, needed for untainted canvas export. */
    public byte[] readDisplay(Picture picture){
        String bucket=picture.getStorageBucket()==null?config.getBucket():picture.getStorageBucket();String key=picture.getCompressedKey()==null?cleanup.keyFromUrl(picture.getUrl()):picture.getCompressedKey();
        if(!config.getBucket().equals(bucket)||key==null||!key.matches("^(public|space)/[1-9][0-9]{0,18}/[A-Za-z0-9_./-]{1,300}$")||key.contains(".."))throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"图片不属于当前可读取存储范围");
        try(COSObject object=cos.getObject(bucket,key);InputStream in=object.getObjectContent();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            long size=object.getObjectMetadata().getContentLength();if(size>8*1024*1024)throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片过大，不能在浏览器编辑");
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片过大");out.write(buffer,0,n);}return out.toByteArray();
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ErrorCode.SYSTEM_ERROR,"读取图片失败，请检查存储配置");}
    }
}
