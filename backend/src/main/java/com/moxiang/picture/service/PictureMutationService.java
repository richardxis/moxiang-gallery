package com.moxiang.picture.service;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.model.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** All HTTP picture writers route here; asset upload is outside the DB/room gate. */
@Service
public class PictureMutationService {
    private final PictureMapper pictures;private final SpaceMapper spaceRows;private final SpaceService spaces;private final UserService users;private final TransactionTemplate transactions;private final PictureWriteGate gate;private final PictureObjectCleanupService cleanup;private final PublicPictureCache cache;
    public PictureMutationService(PictureMapper pictures,SpaceMapper spaceRows,SpaceService spaces,UserService users,TransactionTemplate transactions,PictureWriteGate gate,PictureObjectCleanupService cleanup,PublicPictureCache cache){this.pictures=pictures;this.spaceRows=spaceRows;this.spaces=spaces;this.users=users;this.transactions=transactions;this.gate=gate;this.cleanup=cleanup;this.cache=cache;}
    public void permit(Picture p,User actor,String permission){if(actor==null||actor.getId()==null)throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);User fresh=users.getById(actor.getId());if(fresh==null)throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);if(p.getSpaceId()!=null)spaces.requirePermission(p.getSpaceId(),fresh,permission);else if(!fresh.getId().equals(p.getUserId())&&!users.isAdmin(fresh))throw new BusinessException(ErrorCode.NO_AUTH_ERROR);}
    public Picture require(Long id){Picture p=pictures.selectById(id);if(p==null)throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);return p;}
    public void preflight(Picture old,Long revision,User actor){gate.with(old.getId(),()->{permit(old,actor,SpacePermissions.EDIT);gate.ordinary(old.getId());PictureWriteGate.expected(require(old.getId()),revision);return null;});}
    /** Space first, picture second: consistent with quota and membership transactions. */
    public Picture lockCurrent(Picture peek){if(peek.getSpaceId()!=null&&spaceRows.selectForUpdate(peek.getSpaceId())==null)throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);Picture current=pictures.selectForUpdate(peek.getId());if(current==null)throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);if(!Objects.equals(current.getSpaceId(),peek.getSpaceId()))throw new BusinessException(ErrorCode.CONFLICT_ERROR);return current;}
    public Picture saveUpload(Picture fresh,Picture expected,Long revision,User actor,String taskId){
        if(expected==null){transactions.execute(status->{fresh.setRevision(0L);if(fresh.getSpaceId()!=null)spaces.saveUploaded(fresh,null,actor);else if(pictures.insert(fresh)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR);cleanup.committed(taskId);return null;});if(fresh.getSpaceId()==null)cache.invalidateAfterCommit();return fresh;}
        return gate.with(expected.getId(),()->{gate.ordinary(expected.getId());Picture saved=transactions.execute(status->{Picture current=lockCurrent(expected);permit(current,actor,SpacePermissions.EDIT);PictureWriteGate.expected(current,revision);fresh.setId(current.getId());fresh.setSpaceId(current.getSpaceId());fresh.setUserId(current.getUserId());fresh.setRevision(PictureWriteGate.next(current));if(fresh.getSpaceId()!=null)spaces.saveUploaded(fresh,current,actor);else if(pictures.updateById(fresh)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR);cleanup.committed(taskId);cleanup.retire(current);return fresh;});if(saved.getSpaceId()==null)cache.invalidateAfterCommit();gate.committed(saved,null);return saved;});
    }
    public boolean metadata(Picture update,Long revision,User actor){Picture peek=require(update.getId());permit(peek,actor,SpacePermissions.EDIT);return gate.with(peek.getId(),()->{gate.ordinary(peek.getId());Picture saved=transactions.execute(status->{Picture current=lockCurrent(peek);permit(current,actor,SpacePermissions.EDIT);PictureWriteGate.expected(current,revision);update.setRevision(PictureWriteGate.next(current));update.setEditTime(new Date());if(current.getSpaceId()!=null)spaces.editMetadata(update,current.getSpaceId(),actor);else if(pictures.updateById(update)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR);return require(peek.getId());});if(saved.getSpaceId()==null)cache.invalidateAfterCommit();gate.committed(saved,null);return true;});}
    public boolean delete(Long id,Long revision,User actor){Picture peek=require(id);permit(peek,actor,SpacePermissions.DELETE);return gate.with(id,()->{gate.ordinary(id);transactions.execute(status->{Picture current=lockCurrent(peek);permit(current,actor,SpacePermissions.DELETE);PictureWriteGate.expected(current,revision);Picture version=new Picture();version.setId(id);version.setRevision(PictureWriteGate.next(current));if(pictures.updateById(version)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR);if(current.getSpaceId()!=null)spaces.deletePicture(id,actor);else if(pictures.deleteById(id)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR);cleanup.retire(current);return null;});if(peek.getSpaceId()==null)cache.invalidateAfterCommit();gate.deleted(id);return true;});}
    public boolean review(Picture update,Long revision,User actor){if(actor==null||!users.isAdmin(users.getById(actor.getId())))throw new BusinessException(ErrorCode.NO_AUTH_ERROR);return metadata(update,revision,actor);}
}
