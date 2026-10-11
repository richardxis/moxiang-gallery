package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.space.SpaceMemberRequest;
import com.moxiang.picture.model.entity.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class SpaceMemberService {
    private final SpaceService spaces;
    private final SpaceMapper spaceMapper;
    private final SpaceUserMapper members;
    private final UserService users;
    private final TransactionTemplate transactions;
    public SpaceMemberService(SpaceService spaces, SpaceMapper spaceMapper, SpaceUserMapper members, UserService users, TransactionTemplate transactions) {
        this.spaces = spaces; this.spaceMapper = spaceMapper; this.members = members; this.users = users; this.transactions = transactions;
    }
    public Page<SpaceUser> list(Long spaceId, int current, int size, User actor) {
        ThrowUtils.throwIf(current < 1 || current > 10000 || size < 1 || size > 50, ErrorCode.PARAMS_ERROR);
        team(spaceId, actor);
        return members.selectPage(new Page<>(current, size), new QueryWrapper<SpaceUser>().eq("spaceId", spaceId).orderByAsc("id"));
    }
    public void add(SpaceMemberRequest request, User actor) { mutate(request, actor, "add"); }
    public void change(SpaceMemberRequest request, User actor) { mutate(request, actor, "change"); }
    public void remove(SpaceMemberRequest request, User actor) { mutate(request, actor, "remove"); }
    private Space team(Long id, User actor) {
        Space space = spaces.requirePermission(id, actor, SpacePermissions.MANAGE);
        ThrowUtils.throwIf(space.getSpaceType() != 1, ErrorCode.PARAMS_ERROR, "个人空间没有成员管理");
        return space;
    }
    private void mutate(SpaceMemberRequest request, User actor, String action) {
        ThrowUtils.throwIf(request == null || request.getSpaceId() == null || request.getSpaceId() <= 0
                || request.getUserId() == null || request.getUserId() <= 0, ErrorCode.PARAMS_ERROR);
        String role = request.getSpaceRole() == null ? "viewer" : request.getSpaceRole();
        ThrowUtils.throwIf(!"remove".equals(action) && !Arrays.asList("viewer", "editor", "admin").contains(role), ErrorCode.PARAMS_ERROR);
        try {
            transactions.execute(status -> {
                ThrowUtils.throwIf(spaceMapper.selectForUpdate(request.getSpaceId()) == null, ErrorCode.NOT_FOUND_ERROR);
                Space space = team(request.getSpaceId(), actor);
                ThrowUtils.throwIf(request.getUserId().equals(space.getUserId()), ErrorCode.NO_AUTH_ERROR, "创建者不能被移除或降级");
                SpaceUser existing = members.findMembership(space.getId(), request.getUserId());
                boolean ownerAuthority = actor.getId().equals(space.getUserId()) || users.isAdmin(actor);
                ThrowUtils.throwIf(!ownerAuthority && ("admin".equals(role) && !"remove".equals(action)
                        || existing != null && "admin".equals(existing.getSpaceRole())), ErrorCode.NO_AUTH_ERROR, "仅创建者可委派或管理管理员");
                if ("add".equals(action)) {
                    ThrowUtils.throwIf(existing != null, ErrorCode.OPERATION_ERROR, "成员已存在");
                    ThrowUtils.throwIf(users.getById(request.getUserId()) == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
                    SpaceUser member = new SpaceUser(); member.setSpaceId(space.getId()); member.setUserId(request.getUserId()); member.setSpaceRole(role);
                    member.setCreateTime(new Date()); member.setUpdateTime(new Date());
                    ThrowUtils.throwIf(members.insert(member) != 1, ErrorCode.OPERATION_ERROR);
                } else {
                    ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "成员不存在");
                    int changed;
                    if ("remove".equals(action)) changed = members.deleteById(existing.getId());
                    else { existing.setSpaceRole(role); existing.setUpdateTime(new Date()); changed = members.updateById(existing); }
                    ThrowUtils.throwIf(changed != 1, ErrorCode.OPERATION_ERROR);
                }
                return null;
            });
        } catch (DuplicateKeyException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR, "成员已存在"); }
    }
}
