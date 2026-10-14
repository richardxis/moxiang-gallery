package com.moxiang.picture.controller;

import com.moxiang.picture.common.*;
import com.moxiang.picture.model.dto.picture.*;
import com.moxiang.picture.model.vo.PictureSearchVO.*;
import com.moxiang.picture.service.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/picture")
public class PictureDiscoveryController {
    private final PictureDiscoveryService discovery;
    private final PictureBatchEditService batch;
    private final UserService users;
    public PictureDiscoveryController(PictureDiscoveryService discovery,PictureBatchEditService batch,UserService users){this.discovery=discovery;this.batch=batch;this.users=users;}
    @PostMapping("/search/picture") public BaseResponse<ExternalResult> byPicture(@RequestBody PictureSearchRequest body,HttpServletRequest request){return ResultUtils.success(discovery.byPicture(body,users.getLoginUser(request)));}
    @PostMapping("/search/color") public BaseResponse<List<ColorMatch>> byColor(@RequestBody PictureSearchRequest body,HttpServletRequest request){return ResultUtils.success(discovery.byColor(body,users.getLoginUser(request)));}
    @PostMapping("/edit/batch") public BaseResponse<BatchResult> batch(@RequestBody PictureBatchEditRequest body,HttpServletRequest request){return ResultUtils.success(batch.edit(body,users.getLoginUser(request)));}
}
