package com.moxiang.picture.model.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;
@Data @TableName("picture_edit_save")
public class PictureEditSave {
    @TableId(type=IdType.INPUT) private String requestId;
    private Long pictureId;
    private Long userId;
    private Long expectedRevision;
    private String fingerprint;
    private Long resultRevision;
    private String resultJson;
    private Date createTime;
}
