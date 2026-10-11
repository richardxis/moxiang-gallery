package com.moxiang.picture.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("picture_ai_task")
public class OutPaintingTask {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private Long pictureId;
    private String remoteTaskId;
    private String status;
    private String resultUrl;
    private Date createTime;
    private Date updateTime;
}
