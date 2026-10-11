package com.moxiang.picture.model.vo;

import lombok.Data;
import com.moxiang.picture.model.entity.OutPaintingTask;

@Data
public class OutPaintingTaskVO {
    private String taskId;
    private String taskStatus;
    private String outputImageUrl;
    public static OutPaintingTaskVO from(OutPaintingTask task) {
        OutPaintingTaskVO vo = new OutPaintingTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskStatus(task.getStatus());
        vo.setOutputImageUrl(task.getResultUrl());
        return vo;
    }
}
