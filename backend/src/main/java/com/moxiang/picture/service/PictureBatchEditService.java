package com.moxiang.picture.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.picture.PictureBatchEditRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureSearchVO.BatchResult;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PictureBatchEditService {
    private final PictureMapper pictures;
    private final SpaceMapper spaceRows;
    private final SpaceService spaces;
    private final UserService users;
    private final PictureWriteGate gate;
    private final TransactionTemplate transactions;
    private final PublicPictureCache cache;
    private final ObjectMapper json;
    public PictureBatchEditService(PictureMapper pictures,SpaceMapper spaceRows,SpaceService spaces,UserService users,
            PictureWriteGate gate,TransactionTemplate transactions,PublicPictureCache cache,ObjectMapper json){
        this.pictures=pictures;this.spaceRows=spaceRows;this.spaces=spaces;this.users=users;this.gate=gate;this.transactions=transactions;this.cache=cache;this.json=json;
    }
    public BatchResult edit(PictureBatchEditRequest request,User actor){
        ThrowUtils.throwIf(actor==null||actor.getId()==null,ErrorCode.NOT_LOGIN_ERROR);
        List<Long> ids=validate(request);
        return gate.withMany(ids,()->{
            List<Picture> committed=transactions.execute(status->{
                User fresh=users.getById(actor.getId());ThrowUtils.throwIf(fresh==null,ErrorCode.NOT_LOGIN_ERROR);
                if(request.getSpaceId()!=null){
                    ThrowUtils.throwIf(spaceRows.selectForUpdate(request.getSpaceId())==null,ErrorCode.NOT_FOUND_ERROR);
                    spaces.requirePermission(request.getSpaceId(),fresh,SpacePermissions.EDIT);
                }else ThrowUtils.throwIf(!users.isAdmin(fresh),ErrorCode.NO_AUTH_ERROR,"公共图片批量编辑仅管理员可用");
                Map<Long,Picture> current=new HashMap<>();
                for(Long id:ids.stream().sorted().collect(Collectors.toList())){
                    gate.ordinary(id);
                    Picture row=pictures.selectForUpdate(id);ThrowUtils.throwIf(row==null,ErrorCode.NOT_FOUND_ERROR,"所选图片已删除");
                    ThrowUtils.throwIf(!Objects.equals(row.getSpaceId(),request.getSpaceId()),ErrorCode.NO_AUTH_ERROR,"所选图片不属于同一目标图库");
                    PictureWriteGate.expected(row,request.getExpectedRevisions().get(id));current.put(id,row);
                }
                String tags=encodeTags(request.getTags());List<Picture> changed=new ArrayList<>();int sequence=1;
                // Validate every expanded name before writing any picture.
                for(Long id:ids){
                    Picture before=current.get(id),after=new Picture();BeanUtils.copyProperties(before,after);
                    if(request.getCategory()!=null)after.setCategory(request.getCategory().trim());
                    if(tags!=null)after.setTags(tags);
                    if(request.getNameRule()!=null){
                        String name=request.getNameRule().replace("{序号}",String.valueOf(sequence)).replace("{原名}",before.getName()==null?"":before.getName()).trim();
                        ThrowUtils.throwIf(name.isEmpty()||name.length()>128,ErrorCode.PARAMS_ERROR,"重命名结果须为1—128字符");after.setName(name);
                    }
                    after.setRevision(PictureWriteGate.next(before));after.setEditTime(new Date());after.setUpdateTime(new Date());changed.add(after);sequence++;
                }
                for(Picture row:changed)ThrowUtils.throwIf(pictures.updateById(row)!=1,ErrorCode.OPERATION_ERROR,"批量修改失败，整个批次已回滚");
                return changed;
            });
            if(request.getSpaceId()==null)cache.invalidateAfterCommit();
            BatchResult result=new BatchResult();result.setUpdatedCount(committed.size());
            for(Picture picture:committed){result.getRevisions().put(picture.getId(),picture.getRevision());gate.committed(picture,null);}
            return result;
        });
    }
    private List<Long> validate(PictureBatchEditRequest request){
        ThrowUtils.throwIf(request==null||request.getPictureIdList()==null||request.getPictureIdList().isEmpty()||request.getPictureIdList().size()>100,ErrorCode.PARAMS_ERROR,"每批选择1—100张图片");
        ThrowUtils.throwIf(request.getSpaceId()!=null&&request.getSpaceId()<=0,ErrorCode.PARAMS_ERROR);
        LinkedHashSet<Long> unique=new LinkedHashSet<>(request.getPictureIdList());
        ThrowUtils.throwIf(unique.stream().anyMatch(id->id==null||id<=0),ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getExpectedRevisions()==null||!request.getExpectedRevisions().keySet().equals(unique)
                ||request.getExpectedRevisions().values().stream().anyMatch(revision->revision==null||revision<0),ErrorCode.PARAMS_ERROR,"每张图片须携带当前版本");
        ThrowUtils.throwIf(request.getCategory()==null&&request.getTags()==null&&request.getNameRule()==null,ErrorCode.PARAMS_ERROR,"至少选择一个修改字段");
        ThrowUtils.throwIf(request.getCategory()!=null&&request.getCategory().trim().length()>64,ErrorCode.PARAMS_ERROR,"分类过长");
        if(request.getNameRule()!=null){
            String rule=request.getNameRule();
            ThrowUtils.throwIf(rule.trim().isEmpty()||rule.length()>128||rule.replace("{序号}","").replace("{原名}","").matches(".*[{}].*"),ErrorCode.PARAMS_ERROR,"命名规则仅支持{序号}和{原名}");
        }
        encodeTags(request.getTags());return new ArrayList<>(unique);
    }
    private String encodeTags(List<String> input){
        if(input==null)return null;
        ThrowUtils.throwIf(input.size()>20||input.stream().anyMatch(tag->tag==null||tag.trim().isEmpty()||tag.trim().length()>32),ErrorCode.PARAMS_ERROR,"最多20个非空标签，每个最多32字符");
        try{String encoded=json.writeValueAsString(input.stream().map(String::trim).distinct().collect(Collectors.toList()));
            ThrowUtils.throwIf(encoded.length()>512,ErrorCode.PARAMS_ERROR,"标签总长度过长");return encoded;
        }catch(JsonProcessingException e){throw new BusinessException(ErrorCode.PARAMS_ERROR,"标签格式无效");}
    }
}
