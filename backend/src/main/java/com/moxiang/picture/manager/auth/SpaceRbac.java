package com.moxiang.picture.manager.auth;
import cn.dev33.satoken.stp.StpLogic;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.model.entity.User;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SpaceRbac {
    private final StpLogic logic = new StpLogic("space");
    private String key(Long spaceId, User user) {
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        return spaceId + ":" + user.getId();
    }
    public void check(Long spaceId, User user, String permission) {
        ThrowUtils.throwIf(!logic.hasPermission(key(spaceId, user), permission), ErrorCode.NO_AUTH_ERROR, "无空间操作权限");
    }
    public List<String> permissions(Long spaceId, User user) { return logic.getPermissionList(key(spaceId, user)); }
}
