package com.moxiang.picture.controller;

import com.moxiang.picture.common.*;
import com.moxiang.picture.model.dto.space.SpaceCreateRequest;
import com.moxiang.picture.model.entity.Space;
import com.moxiang.picture.service.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/space")
public class SpaceController {
    private final SpaceService spaces;
    private final UserService users;
    public SpaceController(SpaceService spaces, UserService users) { this.spaces = spaces; this.users = users; }
    @PostMapping("/add") public BaseResponse<Space> create(@RequestBody SpaceCreateRequest body, HttpServletRequest request) {
        return ResultUtils.success(spaces.create(body, users.getLoginUser(request)));
    }
    @GetMapping("/my") public BaseResponse<Space> mine(HttpServletRequest request) {
        return ResultUtils.success(spaces.mine(users.getLoginUser(request)));
    }
    @GetMapping("/get/vo") public BaseResponse<Space> get(@RequestParam Long id, HttpServletRequest request) {
        return ResultUtils.success(spaces.authorized(id, users.getLoginUser(request)));
    }
    @GetMapping("/permissions") public BaseResponse<java.util.List<String>> permissions(@RequestParam Long id, HttpServletRequest request) {
        return ResultUtils.success(spaces.permissions(id, users.getLoginUser(request)));
    }
    @GetMapping("/team/list") public BaseResponse<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Space>> teams(
            @RequestParam(defaultValue="1") int current, @RequestParam(defaultValue="10") int pageSize, HttpServletRequest request) {
        return ResultUtils.success(spaces.joinedTeams(users.getLoginUser(request), current, pageSize));
    }
}
