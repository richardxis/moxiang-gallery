package com.moxiang.picture.controller;

import com.moxiang.picture.common.*;
import com.moxiang.picture.model.dto.picture.OutPaintingRequest;
import com.moxiang.picture.model.vo.OutPaintingTaskVO;
import com.moxiang.picture.service.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/picture/out_painting")
public class OutPaintingController {
    private final OutPaintingService service;
    private final UserService users;
    public OutPaintingController(OutPaintingService service, UserService users) { this.service = service; this.users = users; }
    @PostMapping("/create_task")
    public BaseResponse<OutPaintingTaskVO> create(@RequestBody OutPaintingRequest body, HttpServletRequest request) {
        return ResultUtils.success(service.create(body, users.getLoginUser(request)));
    }
    @GetMapping("/get_task")
    public BaseResponse<OutPaintingTaskVO> query(@RequestParam String taskId, HttpServletRequest request) {
        return ResultUtils.success(service.query(taskId, users.getLoginUser(request)));
    }
}
