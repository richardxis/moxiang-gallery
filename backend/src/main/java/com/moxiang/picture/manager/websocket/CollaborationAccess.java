package com.moxiang.picture.manager.websocket;

import com.moxiang.picture.constant.UserConstant;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.service.*;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpSession;

/** Recheck login, persisted picture scope and EDIT permission on every message/delivery. */
@Component
public class CollaborationAccess {
    private final UserService users;
    private final PictureService pictures;
    private final SpaceService spaces;
    public CollaborationAccess(UserService users, PictureService pictures, SpaceService spaces) {
        this.users = users; this.pictures = pictures; this.spaces = spaces;
    }
    public Binding bind(HttpSession session, User user, Long pictureId) {
        ThrowUtils.throwIf(session == null || user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        Picture picture = pictures.getById(pictureId);
        ThrowUtils.throwIf(picture == null || picture.getSpaceId() == null, ErrorCode.NO_AUTH_ERROR, "协作只开放团队图片");
        Binding binding = new Binding(session, user.getId(), pictureId, picture.getSpaceId());
        verify(binding); return binding;
    }
    public void verify(Binding binding) {
        ThrowUtils.throwIf(binding == null || binding.session == null, ErrorCode.NOT_LOGIN_ERROR);
        Object loggedIn;
        try { loggedIn = binding.session.getAttribute(UserConstant.USER_LOGIN_STATE); }
        catch (IllegalStateException e) { throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); }
        ThrowUtils.throwIf(!(loggedIn instanceof User) || !binding.userId.equals(((User) loggedIn).getId()), ErrorCode.NOT_LOGIN_ERROR);
        User user = users.getById(binding.userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        Picture picture = pictures.getById(binding.pictureId);
        ThrowUtils.throwIf(picture == null || !binding.spaceId.equals(picture.getSpaceId()), ErrorCode.NO_AUTH_ERROR);
        Space space = spaces.requirePermission(binding.spaceId, user, SpacePermissions.EDIT);
        ThrowUtils.throwIf(!Integer.valueOf(1).equals(space.getSpaceType()), ErrorCode.NO_AUTH_ERROR);
    }
    public Picture picture(Binding binding) {
        verify(binding);
        Picture picture=pictures.getById(binding.pictureId);
        ThrowUtils.throwIf(picture==null,ErrorCode.NOT_FOUND_ERROR);
        return picture;
    }
    public static final class Binding {
        public final HttpSession session;
        public final Long userId, pictureId, spaceId;
        public Binding(HttpSession session, Long userId, Long pictureId, Long spaceId) {
            this.session = session; this.userId = userId; this.pictureId = pictureId; this.spaceId = spaceId;
        }
    }
}
