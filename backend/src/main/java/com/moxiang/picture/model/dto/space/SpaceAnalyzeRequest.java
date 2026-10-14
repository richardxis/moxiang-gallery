package com.moxiang.picture.model.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;

/** Exactly one of spaceId / queryPublic / queryAll identifies the analysis scope. */
@Data
public class SpaceAnalyzeRequest {
    private Long spaceId;
    private boolean queryPublic;
    private boolean queryAll;
    private String timeDimension = "day";
    private Long userId;
    private Integer topN = 10;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
