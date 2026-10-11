package com.moxiang.picture.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moxiang.picture.annotation.AuthCheck;
import com.moxiang.picture.common.BaseResponse;
import com.moxiang.picture.common.DeleteRequest;
import com.moxiang.picture.common.ResultUtils;
import com.moxiang.picture.constant.UserConstant;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import com.moxiang.picture.exception.ThrowUtils;
import com.moxiang.picture.model.dto.picture.*;
import com.moxiang.picture.model.entity.Picture;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.model.enums.PictureReviewStatusEnum;
import com.moxiang.picture.model.vo.PictureTagCategory;
import com.moxiang.picture.model.vo.PictureVO;
import com.moxiang.picture.service.PictureService;
import com.moxiang.picture.service.UserService;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.manager.cache.PublicPictureQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;
    @Resource private PublicPictureCache publicPictureCache;
    @Resource private com.moxiang.picture.service.SpaceService spaceService;
    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadRequest uploadRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(uploadRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureService.uploadPicture(uploadRequest.getFileUrl(), uploadRequest, loginUser));
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (oldPicture.getSpaceId() != null) {
            pictureService.deleteVersioned(id,deleteRequest.getExpectedRevision(),loginUser);
            return ResultUtils.success(true);
        }
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = pictureService.deleteVersioned(id,deleteRequest.getExpectedRevision(),loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        if(pictureUpdateRequest.getTags()!=null)picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
        if (oldPicture.getSpaceId() == null) pictureService.fillReviewParams(picture, loginUser);
        else { spaceService.requirePermission(oldPicture.getSpaceId(), loginUser, com.moxiang.picture.manager.auth.SpacePermissions.EDIT); picture.setReviewStatus(1); }
        // 操作数据库
        if (oldPicture.getSpaceId() != null) {
            pictureService.editMetadata(picture,pictureUpdateRequest.getExpectedRevision(),loginUser);
            return ResultUtils.success(true);
        }
        boolean result = pictureService.editMetadata(picture,pictureUpdateRequest.getExpectedRevision(),loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        if (picture.getSpaceId() != null) {
            spaceService.authorized(picture.getSpaceId(), userService.getLoginUser(request));
            return ResultUtils.success(pictureService.getPictureVO(picture, request));
        }
        if (!Integer.valueOf(PictureReviewStatusEnum.PASS.getValue()).equals(picture.getReviewStatus())) {
            User viewer = userService.getLoginUser(request);
            ThrowUtils.throwIf(!viewer.getId().equals(picture.getUserId()) && !userService.isAdmin(viewer),
                    ErrorCode.NO_AUTH_ERROR, "无权查看未审核图片");
        }
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        validatePage(pictureQueryRequest, 100);
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        if (pictureQueryRequest != null && pictureQueryRequest.getSpaceId() != null) {
            validatePage(pictureQueryRequest, 20);
            spaceService.authorized(pictureQueryRequest.getSpaceId(), userService.getLoginUser(request));
            PictureQueryRequest privateQuery = new PictureQueryRequest();
            BeanUtils.copyProperties(pictureQueryRequest, privateQuery);
            privateQuery.setNullSpaceId(false);
            privateQuery.setReviewStatus(null);
            if (privateQuery.getSortField() == null || privateQuery.getSortField().trim().isEmpty()) {
                privateQuery.setSortField("createTime"); privateQuery.setSortOrder("descend");
            }
            return ResultUtils.success(loadPublicPage(privateQuery, request));
        }
        PictureQueryRequest query = PublicPictureQuery.normalize(pictureQueryRequest);
        return ResultUtils.success(loadPublicPage(query, request));
    }

    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest query,
                                                                     HttpServletRequest request) {
        return ResultUtils.success(publicPictureCache.query(query, normalized -> loadPublicPage(normalized, request)));
    }

    private Page<PictureVO> loadPublicPage(PictureQueryRequest query, HttpServletRequest request) {
        Page<Picture> page = pictureService.page(new Page<>(query.getCurrent(), query.getPageSize()),
                pictureService.getQueryWrapper(query));
        return pictureService.getPictureVOPage(page, request);
    }

    @GetMapping("/cache/stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<java.util.Map<String, Object>> cacheStatistics() {
        return ResultUtils.success(publicPictureCache.statistics());
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        if(pictureEditRequest.getTags()!=null)picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        // 补充审核参数
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (oldPicture.getSpaceId() != null) spaceService.requirePermission(oldPicture.getSpaceId(), loginUser, com.moxiang.picture.manager.auth.SpacePermissions.EDIT);
        if (oldPicture.getSpaceId() == null) pictureService.fillReviewParams(picture, loginUser);
        else picture.setReviewStatus(1);
        if (oldPicture.getSpaceId() == null && !oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        if (oldPicture.getSpaceId() != null) {
            pictureService.editMetadata(picture,pictureEditRequest.getExpectedRevision(),loginUser);
            return ResultUtils.success(true);
        }
        boolean result = pictureService.editMetadata(picture,pictureEditRequest.getExpectedRevision(),loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    private void validatePage(PictureQueryRequest query, int maximum) {
        ThrowUtils.throwIf(query == null || query.getCurrent() <= 0 || query.getPageSize() <= 0
                || query.getPageSize() > maximum, ErrorCode.PARAMS_ERROR, "分页参数无效");
    }



}
