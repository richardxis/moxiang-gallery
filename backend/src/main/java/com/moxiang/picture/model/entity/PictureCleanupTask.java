package com.moxiang.picture.model.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;
@Data @TableName("picture_object_cleanup")
public class PictureCleanupTask {
    @TableId(type=IdType.INPUT) private String id;
    private String bucket;
    private String objectKeys;
    private String status;
    private Integer attempts;
    private Date nextRunTime;
    private String claimToken;
    private String lastErrorType;
    private Date createTime;
    private Date updateTime;
}
