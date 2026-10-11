package com.moxiang.picture.model.dto.space;
import lombok.Data;

/** Quotas, owner, type and level are assigned by the server. */
@Data
public class SpaceCreateRequest {
    private String spaceName;
    /** 0 personal, 1 team; quota and owner remain server-assigned. */
    private Integer spaceType = 0;
}
