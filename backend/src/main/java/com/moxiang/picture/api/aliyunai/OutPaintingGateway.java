package com.moxiang.picture.api.aliyunai;

import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import lombok.Data;

public interface OutPaintingGateway {
    void requireEnabled();
    Result create(String imageUrl, OutPaintingRequest.Parameters parameters);
    Result query(String remoteTaskId);

    @Data
    class Result {
        private String taskId;
        private String status;
        private String imageUrl;
    }
}
