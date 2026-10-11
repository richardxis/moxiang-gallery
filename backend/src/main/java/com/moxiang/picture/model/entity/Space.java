package com.moxiang.picture.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

/** Common-tier personal and team spaces; no paid upgrades in this increment. */
@Data
@TableName("space")
public class Space {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String spaceName;
    private Integer spaceLevel;
    private Integer spaceType;
    private Long maxSize;
    private Long maxCount;
    private Long totalSize;
    private Long totalCount;
    private Long userId;
    private Date createTime;
    private Date updateTime;
}
