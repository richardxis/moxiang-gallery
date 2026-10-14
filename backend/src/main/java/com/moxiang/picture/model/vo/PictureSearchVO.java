package com.moxiang.picture.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;

public final class PictureSearchVO {
    private PictureSearchVO() { }
    @Data
    public static class ExternalImage {
        private String thumbUrl;
        private String fromUrl;
    }
    @Data
    public static class ExternalResult {
        private String provider = "baidu";
        private String status = "COMPLETE";
        private String pageUrl;
        private String message;
        private List<ExternalImage> results = new ArrayList<>();
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class ColorMatch extends PictureVO {
        private double similarity;
    }
    @Data
    public static class BatchResult {
        private int updatedCount;
        private Map<Long, Long> revisions = new LinkedHashMap<>();
    }
}
