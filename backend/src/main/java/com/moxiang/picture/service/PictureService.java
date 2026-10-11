package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import com.moxiang.picture.model.dto.picture.PictureUploadByBatchRequest;
import com.moxiang.picture.model.dto.picture.PictureReviewRequest;
import com.moxiang.picture.model.dto.picture.PictureUploadRequest;
import com.moxiang.picture.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author Richard
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-03-19 14:28:32
*/
public interface PictureService extends IService<Picture> {

    /**
     * 校验图片
     *
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 上传图片
     *
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取单张图片信息
     *
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取多张图片信息
     *
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillReviewParams(Picture picture, User loginUser);

    int uploadPictureByBatch(PictureUploadByBatchRequest request, User loginUser);
    boolean editMetadata(Picture update,Long expectedRevision,User actor);
    boolean deleteVersioned(Long id,Long expectedRevision,User actor);
}
