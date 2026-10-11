package com.moxiang.picture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {
    private Long expectedRevision;
  
    /**  
     * 图片 id（用于修改）  
     */  
    private Long id;
    /** null means public gallery; replacement uploads cannot move scopes. */
    private Long spaceId;
    /** URL 导入时的图片地址。 */
    private String fileUrl;
    /** 可选图片名称，用于批量命名。 */
    private String picName;
  
    private static final long serialVersionUID = 1L;  
}
