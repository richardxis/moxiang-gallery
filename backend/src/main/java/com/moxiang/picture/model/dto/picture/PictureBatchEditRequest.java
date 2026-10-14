package com.moxiang.picture.model.dto.picture;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PictureBatchEditRequest {
    private List<Long> pictureIdList;
    /** Null targets public pictures and requires a system administrator. */
    private Long spaceId;
    private String category;
    private List<String> tags;
    /** Supports {序号} and {原名}; null leaves names unchanged. */
    private String nameRule;
    private Map<Long, Long> expectedRevisions;
}
