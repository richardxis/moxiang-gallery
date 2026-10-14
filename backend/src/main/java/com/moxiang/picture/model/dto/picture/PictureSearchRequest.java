package com.moxiang.picture.model.dto.picture;

import lombok.Data;

@Data
public class PictureSearchRequest {
    private Long pictureId;
    private Long spaceId;
    private String picColor;
    private Integer topN = 12;
    private Double minSimilarity = 0.0;
}
