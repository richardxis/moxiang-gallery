package com.moxiang.picture.model.dto.space;
import lombok.Data;

@Data
public class SpaceMemberRequest {
    private Long spaceId;
    private Long userId;
    private String spaceRole;
}
