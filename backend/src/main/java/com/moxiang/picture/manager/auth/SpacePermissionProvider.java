package com.moxiang.picture.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.service.UserService;
import org.springframework.stereotype.Component;
import java.util.*;

/** The scoped account key is built by the server, never accepted from the request body. */
@Component
public class SpacePermissionProvider implements StpInterface {
    private final SpaceMapper spaces;
    private final SpaceUserMapper members;
    private final UserService users;
    public SpacePermissionProvider(SpaceMapper spaces, SpaceUserMapper members, UserService users) {
        this.spaces = spaces; this.members = members; this.users = users;
    }
    @Override public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> roles = getRoleList(loginId, loginType);
        return roles.isEmpty() ? Collections.emptyList() : SpacePermissions.forRole(roles.get(0));
    }
    @Override public List<String> getRoleList(Object loginId, String loginType) {
        if (!"space".equals(loginType) || loginId == null) return Collections.emptyList();
        String key = loginId.toString();
        if (!key.matches("[1-9][0-9]{0,18}:[1-9][0-9]{0,18}")) return Collections.emptyList();
        String[] parts = key.split(":");
        Long spaceId, userId;
        try { spaceId = Long.valueOf(parts[0]); userId = Long.valueOf(parts[1]); }
        catch (NumberFormatException e) { return Collections.emptyList(); }
        Space space = spaces.selectById(spaceId); User user = users.getById(userId);
        if (space == null || user == null || !(Integer.valueOf(0).equals(space.getSpaceType()) || Integer.valueOf(1).equals(space.getSpaceType())))
            return Collections.emptyList();
        if (users.isAdmin(user) || userId.equals(space.getUserId())) return Collections.singletonList("admin");
        if (!Integer.valueOf(1).equals(space.getSpaceType())) return Collections.emptyList();
        SpaceUser member = members.findMembership(spaceId, userId);
        if (member == null || SpacePermissions.forRole(member.getSpaceRole()).isEmpty()) return Collections.emptyList();
        return Collections.singletonList(member.getSpaceRole());
    }
}
