package com.moxiang.picture.model.dto.picture;

import lombok.Data;

/** Frontend uses camelCase; the gateway alone translates to vendor snake_case. */
@Data
public class OutPaintingRequest {
    private Long pictureId;
    private Parameters parameters;

    @Data
    public static class Parameters {
        private Integer angle = 0;
        private String outputRatio;
        private Double xScale;
        private Double yScale;
    }
}
