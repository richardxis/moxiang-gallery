package com.moxiang.picture.controller;

import com.moxiang.picture.common.*;
import com.moxiang.picture.model.dto.space.SpaceAnalyzeRequest;
import com.moxiang.picture.model.vo.SpaceAnalyzeVO.*;
import com.moxiang.picture.service.SpaceAnalyzeService;
import com.moxiang.picture.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {
    private final SpaceAnalyzeService analysis;
    private final UserService users;
    public SpaceAnalyzeController(SpaceAnalyzeService analysis, UserService users) {
        this.analysis = analysis; this.users = users;
    }
    @PostMapping("/usage")
    public BaseResponse<Usage> usage(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return ResultUtils.success(analysis.usage(body, users.getLoginUser(request)));
    }
    @PostMapping("/category")
    public BaseResponse<List<Category>> category(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return ResultUtils.success(analysis.category(body, users.getLoginUser(request)));
    }
    @PostMapping("/tag")
    public BaseResponse<List<Tag>> tag(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return ResultUtils.success(analysis.tag(body, users.getLoginUser(request)));
    }
    @PostMapping("/size")
    public BaseResponse<List<SizeBucket>> size(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return ResultUtils.success(analysis.size(body, users.getLoginUser(request)));
    }
    @PostMapping("/user")
    public BaseResponse<List<UserActivity>> user(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return ResultUtils.success(analysis.user(body, users.getLoginUser(request)));
    }
    @PostMapping("/rank")
    public BaseResponse<List<Rank>> rank(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return ResultUtils.success(analysis.rank(body, users.getLoginUser(request)));
    }
}
