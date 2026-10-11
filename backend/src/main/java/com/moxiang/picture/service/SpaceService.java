package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.space.SpaceCreateRequest;
import com.moxiang.picture.model.entity.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import com.moxiang.picture.manager.auth.*;

@Service
public class SpaceService {
    private final SpaceMapper spaces;
    private final PictureMapper pictures;
    private final UserService users;
    private final TransactionTemplate transactions;
    @javax.annotation.Resource private SpaceUserMapper members;
    @javax.annotation.Resource private SpaceRbac rbac;
    public SpaceService(SpaceMapper spaces, PictureMapper pictures, UserService users, TransactionTemplate transactions) {
        this.spaces = spaces; this.pictures = pictures; this.users = users; this.transactions = transactions;
    }
    public Space create(SpaceCreateRequest request, User user) {
        requireUser(user);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        int type = request.getSpaceType() == null ? 0 : request.getSpaceType();
        ThrowUtils.throwIf(type != 0 && type != 1, ErrorCode.PARAMS_ERROR, "空间类型无效");
        String name = request.getSpaceName() == null ? "我的墨象空间" : request.getSpaceName().trim();
        ThrowUtils.throwIf(name.isEmpty() || name.length() > 128, ErrorCode.PARAMS_ERROR, "空间名称须为 1—128 字符");
        Space space = new Space(); space.setSpaceName(name); space.setUserId(user.getId());
        space.setSpaceLevel(0); space.setSpaceType(type); space.setMaxSize(100L * 1024 * 1024); space.setMaxCount(100L);
        space.setTotalSize(0L); space.setTotalCount(0L); space.setCreateTime(new Date()); space.setUpdateTime(new Date());
        try {
            transactions.execute(status -> {
                ThrowUtils.throwIf(spaces.insert(space) != 1, ErrorCode.OPERATION_ERROR, "空间创建失败");
                if (type == 1) {
                    SpaceUser owner = new SpaceUser(); owner.setSpaceId(space.getId()); owner.setUserId(user.getId()); owner.setSpaceRole("admin");
                    owner.setCreateTime(new Date()); owner.setUpdateTime(new Date());
                    ThrowUtils.throwIf(members.insert(owner) != 1, ErrorCode.OPERATION_ERROR, "创建者成员登记失败");
                }
                return null;
            });
        } catch (DuplicateKeyException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR, "每人每种类型只能创建一个空间"); }
        return space;
    }
    public Space mine(User user) {
        requireUser(user);
        return spaces.selectOne(new QueryWrapper<Space>().eq("userId", user.getId()).eq("spaceType", 0));
    }
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Space> joinedTeams(User user, int current, int size) {
        requireUser(user);
        ThrowUtils.throwIf(current < 1 || current > 10000 || size < 1 || size > 20, ErrorCode.PARAMS_ERROR);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Space> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size, spaces.countJoinedTeams(user.getId()));
        page.setRecords(spaces.joinedTeams(user.getId(), size, (current - 1L) * size));
        return page;
    }
    public Space authorized(Long id, User user) {
        return requirePermission(id, user, SpacePermissions.VIEW);
    }
    public Space requirePermission(Long id, User user, String permission) {
        requireUser(user);
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spaces.selectById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (Integer.valueOf(0).equals(space.getSpaceType())) {
            ThrowUtils.throwIf(!user.getId().equals(space.getUserId()) && !users.isAdmin(user), ErrorCode.NO_AUTH_ERROR);
        } else if (Integer.valueOf(1).equals(space.getSpaceType())) rbac.check(id, user, permission);
        else throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "未知空间类型");
        return space;
    }
    public List<String> permissions(Long id, User user) {
        Space space = authorized(id, user);
        return space.getSpaceType() == 0 ? SpacePermissions.forRole("admin") : rbac.permissions(id, user);
    }
    public void checkUpload(Long id, User user) {
        Space space = requirePermission(id, user, SpacePermissions.UPLOAD);
        ThrowUtils.throwIf(space.getTotalCount() >= space.getMaxCount() || space.getTotalSize() >= space.getMaxSize(),
                ErrorCode.OPERATION_ERROR, "空间额度已满");
    }
    /** External upload has finished; only quota and picture writes share this transaction. */
    public void saveUploaded(Picture picture, Picture expected, User user) {
        ThrowUtils.throwIf(picture.getSpaceId() == null || picture.getPicSize() == null || picture.getPicSize() <= 0,
                ErrorCode.PARAMS_ERROR);
        transactions.execute(status -> {
            ThrowUtils.throwIf(spaces.selectForUpdate(picture.getSpaceId()) == null, ErrorCode.NOT_FOUND_ERROR);
            requirePermission(picture.getSpaceId(), user, expected == null ? SpacePermissions.UPLOAD : SpacePermissions.EDIT);
            long previousSize = 0;
            if (expected != null) {
                Picture current = pictures.selectForUpdate(expected.getId());
                ThrowUtils.throwIf(current == null, ErrorCode.NOT_FOUND_ERROR);
                ThrowUtils.throwIf(!Objects.equals(current.getSpaceId(), picture.getSpaceId())
                        || !Objects.equals(current.getUrl(), expected.getUrl())
                        || !Objects.equals(current.getPicSize(), expected.getPicSize()),
                        ErrorCode.OPERATION_ERROR, "图片已被修改，请刷新后重试");
                previousSize = current.getPicSize() == null ? 0 : current.getPicSize();
            }
            long countDelta = expected == null ? 1 : 0;
            ThrowUtils.throwIf(spaces.adjustUsage(picture.getSpaceId(), picture.getPicSize() - previousSize, countDelta) != 1,
                    ErrorCode.OPERATION_ERROR, "空间额度不足或统计异常");
            int changed = expected == null ? pictures.insert(picture) : pictures.updateById(picture);
            ThrowUtils.throwIf(changed != 1, ErrorCode.OPERATION_ERROR, "空间图片保存失败");
            return null;
        });
    }
    public void deletePicture(Long id, User user) {
        // Scope lookup is outside the transaction: do not establish an old RR snapshot before waiting for the space lock.
        Picture initial = pictures.selectById(id);
        ThrowUtils.throwIf(initial == null || initial.getSpaceId() == null, ErrorCode.NOT_FOUND_ERROR);
        transactions.execute(status -> {
            ThrowUtils.throwIf(spaces.selectForUpdate(initial.getSpaceId()) == null, ErrorCode.NOT_FOUND_ERROR);
            Picture picture = pictures.selectForUpdate(id);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
            ThrowUtils.throwIf(!Objects.equals(initial.getSpaceId(), picture.getSpaceId()), ErrorCode.OPERATION_ERROR, "图片空间已变化");
            requirePermission(picture.getSpaceId(), user, SpacePermissions.DELETE);
            ThrowUtils.throwIf(pictures.deleteById(id) != 1, ErrorCode.OPERATION_ERROR, "图片删除失败");
            long size = picture.getPicSize() == null ? 0 : picture.getPicSize();
            ThrowUtils.throwIf(spaces.adjustUsage(picture.getSpaceId(), -size, -1) != 1,
                    ErrorCode.OPERATION_ERROR, "空间统计异常，删除已回滚");
            return null;
        });
    }
    public void editMetadata(Picture update, Long spaceId, User user) {
        transactions.execute(status -> {
            ThrowUtils.throwIf(spaces.selectForUpdate(spaceId) == null, ErrorCode.NOT_FOUND_ERROR);
            requirePermission(spaceId, user, SpacePermissions.EDIT);
            Picture current = pictures.selectForUpdate(update.getId());
            ThrowUtils.throwIf(current == null || !spaceId.equals(current.getSpaceId()), ErrorCode.NOT_FOUND_ERROR);
            ThrowUtils.throwIf(pictures.updateById(update) != 1, ErrorCode.OPERATION_ERROR);
            return null;
        });
    }
    private void requireUser(User user) { ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR); }
}
