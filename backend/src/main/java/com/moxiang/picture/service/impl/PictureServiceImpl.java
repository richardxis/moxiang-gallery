package com.moxiang.picture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import com.moxiang.picture.exception.ThrowUtils;
import com.moxiang.picture.manager.upload.FilePictureUpload;
import com.moxiang.picture.manager.upload.UrlPictureUpload;
import com.moxiang.picture.manager.upload.PictureUploadTemplate;
import com.moxiang.picture.manager.PictureBatchSource;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.model.dto.picture.PictureUploadByBatchRequest;
import lombok.extern.slf4j.Slf4j;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import com.moxiang.picture.model.dto.picture.PictureReviewRequest;
import com.moxiang.picture.model.dto.picture.PictureUploadRequest;
import com.moxiang.picture.model.entity.Picture;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.model.enums.PictureReviewStatusEnum;
import com.moxiang.picture.model.vo.PictureVO;
import com.moxiang.picture.model.vo.UserVO;
import com.moxiang.picture.service.PictureService;
import com.moxiang.picture.mapper.PictureMapper;
import com.moxiang.picture.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author Richard
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-03-19 14:28:32
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource private UrlPictureUpload urlPictureUpload;
    @Resource private PictureBatchSource pictureBatchSource;
    @Resource private PublicPictureCache publicPictureCache;
    @Resource private com.moxiang.picture.service.SpaceService spaceService;
    @Resource private com.moxiang.picture.service.PictureMutationService mutations;
    @Resource private com.moxiang.picture.service.PictureAssetService assets;
    @Resource private com.moxiang.picture.service.PictureObjectCleanupService cleanup;

    @Resource
    private UserService userService;

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 512, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 512, ErrorCode.PARAMS_ERROR, "简介过长");
        }
        ThrowUtils.throwIf(picture.getName() != null && picture.getName().length() > 128,
                ErrorCode.PARAMS_ERROR, "名称过长");
        ThrowUtils.throwIf(picture.getCategory() != null && picture.getCategory().length() > 64,
                ErrorCode.PARAMS_ERROR, "分类过长");
        ThrowUtils.throwIf(picture.getTags() != null && picture.getTags().length() > 512,
                ErrorCode.PARAMS_ERROR, "标签过长");
    }


    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 用于判断是新增还是更新图片
        Long pictureId = null;
        Long spaceId = pictureUploadRequest == null ? null : pictureUploadRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId != null && spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 无效");
        Picture previousPicture = null;
        Long ownerId = loginUser.getId();
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
            ThrowUtils.throwIf(pictureId != null && pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 无效");
            ThrowUtils.throwIf(StrUtil.isNotBlank(pictureUploadRequest.getPicName())
                    && pictureUploadRequest.getPicName().length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        }
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            previousPicture = oldPicture;
            ThrowUtils.throwIf(spaceId != null && !spaceId.equals(oldPicture.getSpaceId()),
                    ErrorCode.PARAMS_ERROR, "不能通过替换上传迁移图片空间");
            spaceId = oldPicture.getSpaceId();
            ownerId = oldPicture.getUserId();
            // 仅本人或管理员可编辑
            if (oldPicture.getSpaceId() == null && !oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录
        if (spaceId != null) {
            spaceService.requirePermission(spaceId, loginUser, previousPicture == null
                    ? com.moxiang.picture.manager.auth.SpacePermissions.UPLOAD : com.moxiang.picture.manager.auth.SpacePermissions.EDIT);
            if (previousPicture == null) spaceService.checkUpload(spaceId, loginUser);
        }
        String uploadPathPrefix = spaceId == null ? String.format("public/%s", loginUser.getId())
                : String.format("space/%s", spaceId);
        if(previousPicture!=null)mutations.preflight(previousPicture,pictureUploadRequest.getExpectedRevision(),loginUser);
        com.moxiang.picture.service.PictureAssetService.Prepared prepared=assets.prepare(uploadPathPrefix);
        try {
        UploadPictureResult uploadPictureResult = assets.upload(inputSource,prepared);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setPicColor(uploadPictureResult.getPicColor());
        picture.setName(uploadPictureResult.getPicName());
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picture.setName(pictureUploadRequest.getPicName().trim());
        }
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(ownerId);
        picture.setSpaceId(spaceId);
        assets.copyKeys(picture,uploadPictureResult);
        // 补充审核参数
        if (spaceId == null) this.fillReviewParams(picture, loginUser);
        else picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 操作数据库
        picture=mutations.saveUpload(picture,previousPicture,pictureUploadRequest==null?null:pictureUploadRequest.getExpectedRevision(),loginUser,prepared.task.getId());
        return PictureVO.objToVo(picture);
        } catch(RuntimeException e){cleanup.failed(prepared.task);throw e;}
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }



    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(spaceId != null, "spaceId", spaceId);
        queryWrapper.isNull(pictureQueryRequest.isNullSpaceId(), "spaceId");
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            ThrowUtils.throwIf(!java.util.Arrays.asList("id", "createTime", "editTime", "updateTime",
                    "picSize", "picWidth", "picHeight").contains(sortField), ErrorCode.PARAMS_ERROR, "排序字段不支持");
            ThrowUtils.throwIf(!"ascend".equals(sortOrder) && !"descend".equals(sortOrder),
                    ErrorCode.PARAMS_ERROR, "排序方向不支持");
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
            if (!"id".equals(sortField)) queryWrapper.orderBy(true, "ascend".equals(sortOrder), "id");
        }
        return queryWrapper;
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || id <= 0 || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String message = pictureReviewRequest.getReviewMessage();
        ThrowUtils.throwIf(message != null && message.length() > 512, ErrorCode.PARAMS_ERROR, "审核说明过长");
        ThrowUtils.throwIf(PictureReviewStatusEnum.REJECT.equals(reviewStatusEnum) && StrUtil.isBlank(message),
                ErrorCode.PARAMS_ERROR, "拒绝时请填写审核说明");
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = mutations.review(updatePicture,pictureReviewRequest.getExpectedRevision(),loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }
    @Override public boolean editMetadata(Picture update,Long expectedRevision,User actor){return mutations.metadata(update,expectedRevision,actor);}
    @Override public boolean deleteVersioned(Long id,Long expectedRevision,User actor){return mutations.delete(id,expectedRevision,actor);}

    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest request, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getSearchText()),
                ErrorCode.PARAMS_ERROR, "搜索词不能为空");
        Integer count = request.getCount();
        ThrowUtils.throwIf(count == null || count <= 0 || count > 30, ErrorCode.PARAMS_ERROR, "抓取数量须为 1—30");
        ThrowUtils.throwIf(request.getSearchText().length() > 100, ErrorCode.PARAMS_ERROR, "搜索词过长");
        String prefix = StrUtil.blankToDefault(request.getNamePrefix(), request.getSearchText()).trim();
        ThrowUtils.throwIf(prefix.length() > 80, ErrorCode.PARAMS_ERROR, "名称前缀过长");
        urlPictureUpload.requireEnabled();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        int success = 0;
        for (String url : pictureBatchSource.search(request.getSearchText(), count * 3)) {
            if (success >= count || System.nanoTime() > deadline) break;
            PictureUploadRequest upload = new PictureUploadRequest();
            upload.setPicName(prefix + (success + 1));
            try {
                uploadPicture(url, upload, loginUser);
                success++;
            } catch (Exception e) {
                // 单条失败继续，响应数字只统计已成功入库的图片。
                log.warn("批量图片导入跳过失败候选，异常类型={}", e.getClass().getSimpleName());
            }
        }
        ThrowUtils.throwIf(success == 0, ErrorCode.OPERATION_ERROR, "没有成功导入的图片，请检查来源、存储及数据库配置");
        return success;
    }


}




