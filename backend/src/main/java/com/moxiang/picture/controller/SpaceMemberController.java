package com.moxiang.picture.controller;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moxiang.picture.common.*;
import com.moxiang.picture.model.dto.space.SpaceMemberRequest;
import com.moxiang.picture.model.entity.SpaceUser;
import com.moxiang.picture.service.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/space_user")
public class SpaceMemberController {
    private final SpaceMemberService members;
    private final UserService users;
    public SpaceMemberController(SpaceMemberService members, UserService users) { this.members = members; this.users = users; }
    @GetMapping("/list") public BaseResponse<Page<SpaceUser>> list(@RequestParam Long spaceId, @RequestParam(defaultValue="1") int current,
            @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        return ResultUtils.success(members.list(spaceId, current, pageSize, users.getLoginUser(request)));
    }
    @PostMapping("/add") public BaseResponse<Boolean> add(@RequestBody SpaceMemberRequest body, HttpServletRequest request) {
        members.add(body, users.getLoginUser(request)); return ResultUtils.success(true);
    }
    @PostMapping("/edit") public BaseResponse<Boolean> edit(@RequestBody SpaceMemberRequest body, HttpServletRequest request) {
        members.change(body, users.getLoginUser(request)); return ResultUtils.success(true);
    }
    @PostMapping("/delete") public BaseResponse<Boolean> remove(@RequestBody SpaceMemberRequest body, HttpServletRequest request) {
        members.remove(body, users.getLoginUser(request)); return ResultUtils.success(true);
    }
}
