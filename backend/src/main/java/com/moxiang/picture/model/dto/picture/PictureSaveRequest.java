package com.moxiang.picture.model.dto.picture;
import lombok.Data;
import lombok.ToString;

@Data
public class PictureSaveRequest {
    private Long id;
    private Long expectedRevision;
    private String requestId;
    @ToString.Exclude private String connectionId;
    @ToString.Exclude private String lockToken;
    private String epoch;
    private String draftVersion;
    private Integer cropX;
    private Integer cropY;
    private Integer cropWidth;
    private Integer cropHeight;
}
