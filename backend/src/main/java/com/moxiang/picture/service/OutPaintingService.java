package com.moxiang.picture.service;

import com.moxiang.picture.api.aliyunai.OutPaintingGateway;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.OutPaintingTaskMapper;
import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.OutPaintingTaskVO;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;

@Service
public class OutPaintingService {
    private final PictureService pictures;
    private final UserService users;
    private final OutPaintingTaskMapper tasks;
    private final OutPaintingGateway gateway;
    public OutPaintingService(PictureService pictures, UserService users, OutPaintingTaskMapper tasks, OutPaintingGateway gateway) {
        this.pictures = pictures; this.users = users; this.tasks = tasks; this.gateway = gateway;
    }
    public OutPaintingTaskVO create(OutPaintingRequest request, User user) {
        ThrowUtils.throwIf(request == null || request.getPictureId() == null || request.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Picture picture = authorizedPicture(request.getPictureId(), user);
        validate(request.getParameters(), picture);
        gateway.requireEnabled();
        OutPaintingTask task = new OutPaintingTask();
        task.setId(UUID.randomUUID().toString()); task.setUserId(user.getId()); task.setPictureId(picture.getId());
        task.setStatus("SUBMITTING"); task.setCreateTime(new Date()); task.setUpdateTime(new Date());
        ThrowUtils.throwIf(tasks.insert(task) != 1, ErrorCode.OPERATION_ERROR, "任务登记失败");
        OutPaintingGateway.Result remote;
        try { remote = gateway.create(picture.getUrl(), request.getParameters()); }
        catch (BusinessException e) {
            // An uncertain submission must not be labeled FAILED or automatically reissued.
            task.setStatus("SUBMISSION_UNKNOWN"); task.setUpdateTime(new Date());
            try { tasks.updateById(task); } catch (RuntimeException ignored) { /* retain initial audit row */ }
            throw e;
        }
        task.setRemoteTaskId(remote.getTaskId()); task.setStatus(remote.getStatus()); task.setResultUrl(remote.getImageUrl());
        task.setUpdateTime(new Date());
        ThrowUtils.throwIf(tasks.updateById(task) != 1, ErrorCode.OPERATION_ERROR, "任务已提交但登记未完成，请勿重复创建");
        return OutPaintingTaskVO.from(task);
    }
    public OutPaintingTaskVO query(String id, User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(id == null || !id.matches("[0-9a-fA-F-]{36}"), ErrorCode.PARAMS_ERROR, "任务 ID 无效");
        OutPaintingTask task = tasks.selectById(id);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!user.getId().equals(task.getUserId()) && !users.isAdmin(user), ErrorCode.NO_AUTH_ERROR);
        authorizedPicture(task.getPictureId(), user);
        if (terminal(task.getStatus()) || task.getRemoteTaskId() == null) return OutPaintingTaskVO.from(task);
        // Vendor tasks have a finite query window; no perpetual polling of missing results.
        if (task.getCreateTime().getTime() < System.currentTimeMillis() - 24L * 60 * 60 * 1000) {
            task.setStatus("EXPIRED");
        } else {
            OutPaintingGateway.Result remote = gateway.query(task.getRemoteTaskId());
            ThrowUtils.throwIf(!task.getRemoteTaskId().equals(remote.getTaskId()), ErrorCode.AI_UPSTREAM_ERROR, "任务响应不匹配");
            task.setStatus(remote.getStatus()); task.setResultUrl(remote.getImageUrl());
        }
        task.setUpdateTime(new Date());
        // Conditional update prevents stale concurrent RUNNING responses overwriting a terminal state.
        int changed = tasks.update(task, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OutPaintingTask>()
                .eq("id", id).notIn("status", Arrays.asList("SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN", "EXPIRED")));
        if (changed == 0) task = tasks.selectById(id);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR);
        return OutPaintingTaskVO.from(task);
    }
    private boolean terminal(String status) {
        return Arrays.asList("SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN", "EXPIRED", "SUBMISSION_UNKNOWN").contains(status);
    }
    private Picture authorizedPicture(Long id, User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        Picture picture = pictures.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // Team/space authorization is not yet implemented: deny rather than bypass it.
        ThrowUtils.throwIf(picture.getSpaceId() != null, ErrorCode.NO_AUTH_ERROR, "空间扩图待权限模块完成后开放");
        ThrowUtils.throwIf(!user.getId().equals(picture.getUserId()) && !users.isAdmin(user), ErrorCode.NO_AUTH_ERROR);
        return picture;
    }
    static void validate(OutPaintingRequest.Parameters parameters, Picture picture) {
        ThrowUtils.throwIf(parameters == null, ErrorCode.PARAMS_ERROR, "扩图参数不能为空");
        int angle = parameters.getAngle() == null ? 0 : parameters.getAngle();
        ThrowUtils.throwIf(angle < 0 || angle > 359, ErrorCode.PARAMS_ERROR, "旋转角度无效");
        String ratio = parameters.getOutputRatio();
        boolean hasRatio = ratio != null && !ratio.isEmpty();
        ThrowUtils.throwIf(hasRatio && !Arrays.asList("1:1", "3:4", "4:3", "9:16", "16:9").contains(ratio),
                ErrorCode.PARAMS_ERROR, "宽高比不支持");
        Double x = parameters.getXScale(), y = parameters.getYScale();
        for (Double scale : Arrays.asList(x, y)) {
            ThrowUtils.throwIf(scale != null && (!Double.isFinite(scale) || scale < 1 || scale > 3),
                    ErrorCode.PARAMS_ERROR, "扩展倍数须为 1—3");
        }
        ThrowUtils.throwIf(hasRatio && (x != null || y != null), ErrorCode.PARAMS_ERROR, "请只选择一种扩图方式");
        ThrowUtils.throwIf(!hasRatio && !(x != null && x > 1 || y != null && y > 1),
                ErrorCode.PARAMS_ERROR, "至少一个扩展倍数须大于 1");
        ThrowUtils.throwIf(picture.getPicWidth() == null || picture.getPicHeight() == null
                || picture.getPicWidth() < 512 || picture.getPicHeight() < 512
                || picture.getPicWidth() > 4096 || picture.getPicHeight() > 4096
                || picture.getPicSize() == null || picture.getPicSize() <= 0 || picture.getPicSize() > 10L * 1024 * 1024,
                ErrorCode.PARAMS_ERROR, "图片尺寸或体积不符合扩图要求");
        ThrowUtils.throwIf(picture.getPicFormat() == null || !Arrays.asList("jpg", "jpeg", "png", "heif", "webp")
                .contains(picture.getPicFormat().toLowerCase(Locale.ROOT)), ErrorCode.PARAMS_ERROR, "图片格式不支持扩图");
        try {
            URI uri = URI.create(picture.getUrl());
            ThrowUtils.throwIf(!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null,
                    ErrorCode.PARAMS_ERROR, "图片需使用可访问的 HTTPS 地址");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址无效");
        }
    }
}
