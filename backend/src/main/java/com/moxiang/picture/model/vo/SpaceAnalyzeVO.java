package com.moxiang.picture.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Analysis views expose aggregates, not picture URLs or member profiles. */
public final class SpaceAnalyzeVO {
    private SpaceAnalyzeVO() { }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Usage {
        private long usedSize;
        private long usedCount;
        private Long maxSize;
        private Long maxCount;
        private Double sizeUsageRatio;
        private Double countUsageRatio;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Category {
        private String category;
        private long count;
        private long totalSize;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Tag {
        private String tag;
        private long count;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SizeBucket {
        private String sizeRange;
        private long count;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UserActivity {
        private String period;
        private long count;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Rank {
        private Long id;
        private String spaceName;
        private Long userId;
        private long totalSize;
        private long totalCount;
    }
}
