package com.moxiang.picture.manager.auth;
import java.util.*;

public final class SpacePermissions {
    private SpacePermissions() { }
    public static final String VIEW = "picture:view", UPLOAD = "picture:upload", EDIT = "picture:edit",
            DELETE = "picture:delete", MANAGE = "spaceUser:manage";
    public static List<String> forRole(String role) {
        if ("admin".equals(role)) return Arrays.asList(VIEW, UPLOAD, EDIT, DELETE, MANAGE);
        if ("editor".equals(role)) return Arrays.asList(VIEW, UPLOAD, EDIT, DELETE);
        if ("viewer".equals(role)) return Collections.singletonList(VIEW);
        return Collections.emptyList();
    }
}
