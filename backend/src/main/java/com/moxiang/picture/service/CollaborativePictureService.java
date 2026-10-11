package com.moxiang.picture.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.model.dto.picture.PictureSaveRequest;
import com.moxiang.picture.model.dto.file.UploadPictureResult;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureVO;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpSession;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

@Service
public class CollaborativePictureService {
    private final PictureMutationService mutations;private final SpaceService spaces;private final PictureAssetService assets;private final PictureObjectCleanupService cleanup;private final PictureEditSaveMapper saves;private final PictureMapper pictures;private final TransactionTemplate transactions;private final PictureWriteGate gate;private final ObjectMapper json;
    public CollaborativePictureService(PictureMutationService mutations,SpaceService spaces,PictureAssetService assets,PictureObjectCleanupService cleanup,PictureEditSaveMapper saves,PictureMapper pictures,TransactionTemplate transactions,PictureWriteGate gate,ObjectMapper json){this.mutations=mutations;this.spaces=spaces;this.assets=assets;this.cleanup=cleanup;this.saves=saves;this.pictures=pictures;this.transactions=transactions;this.gate=gate;this.json=json;}
    public PictureVO save(MultipartFile file,PictureSaveRequest request,User actor,HttpSession session){
        validateRequest(request);int[] dimensions=pngDimensions(file);String fingerprint=fingerprint(file,request,actor);
        Picture peek=mutations.require(request.getId());authorize(peek,actor);
        PictureVO replay=gate.with(peek.getId(),()->{PictureEditSave old=saves.selectById(request.getRequestId());if(old!=null)return replay(old,fingerprint,actor,peek.getId());PictureWriteGate.expected(peek,request.getExpectedRevision());PictureWriteGate.Snapshot snapshot=gate.validate(request,actor,session);bounds(peek,snapshot,request,dimensions);return null;});
        if(replay!=null)return replay;
        PictureAssetService.Prepared prepared=assets.prepare("space/"+peek.getSpaceId());
        try{
            UploadPictureResult uploaded=assets.upload(file,prepared);
            if(uploaded.getPicWidth()!=dimensions[0]||uploaded.getPicHeight()!=dimensions[1])throw new BusinessException(ErrorCode.PARAMS_ERROR,"处理后的图片尺寸不符");
            return gate.with(peek.getId(),()->{
                Commit result=transactions.execute(status->{
                    Picture current=mutations.lockCurrent(peek);authorize(current,actor);
                    PictureEditSave old=saves.selectById(request.getRequestId());if(old!=null)return new Commit(replay(old,fingerprint,actor,current.getId()),null,true);
                    PictureWriteGate.expected(current,request.getExpectedRevision());bounds(current,gate.validate(request,actor,session),request,dimensions);
                    Picture fresh=new Picture();BeanUtils.copyProperties(current,fresh);fresh.setRevision(PictureWriteGate.next(current));fresh.setUrl(uploaded.getUrl());fresh.setThumbnailUrl(uploaded.getThumbnailUrl());fresh.setPicColor(uploaded.getPicColor());fresh.setPicSize(uploaded.getPicSize());fresh.setPicWidth(uploaded.getPicWidth());fresh.setPicHeight(uploaded.getPicHeight());fresh.setPicScale(uploaded.getPicScale());fresh.setPicFormat(uploaded.getPicFormat());fresh.setEditTime(new Date());fresh.setUpdateTime(new Date());assets.copyKeys(fresh,uploaded);
                    spaces.saveUploaded(fresh,current,actor);cleanup.committed(prepared.task.getId());cleanup.retire(current);
                    PictureVO view=PictureVO.objToVo(fresh);PictureEditSave operation=new PictureEditSave();operation.setRequestId(request.getRequestId());operation.setPictureId(current.getId());operation.setUserId(actor.getId());operation.setExpectedRevision(request.getExpectedRevision());operation.setFingerprint(fingerprint);operation.setResultRevision(fresh.getRevision());operation.setCreateTime(new Date());
                    try{operation.setResultJson(json.writeValueAsString(view));}catch(Exception e){throw new IllegalStateException("save result encoding failed");}
                    if(saves.insert(operation)!=1)throw new BusinessException(ErrorCode.OPERATION_ERROR,"保存结果登记失败");
                    // Recheck after DB writes, immediately before commit; the gate stays held through commit.
                    gate.validate(request,actor,session);
                    return new Commit(view,fresh,false);
                });
                if(result.replayed)cleanup.failed(prepared.task);else gate.committed(result.picture,request.getRequestId());
                return result.view;
            });
        }catch(RuntimeException e){cleanup.failed(prepared.task);if(e instanceof DuplicateKeyException)throw new BusinessException(ErrorCode.CONFLICT_ERROR,"保存请求编号已被使用");throw e;}
    }
    public PictureVO result(String requestId,User actor){if(requestId==null||!requestId.matches("[0-9a-fA-F-]{36}"))throw new BusinessException(ErrorCode.PARAMS_ERROR);PictureEditSave operation=saves.selectById(requestId);if(operation==null)throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"尚无已提交结果");if(!Objects.equals(operation.getUserId(),actor.getId()))throw new BusinessException(ErrorCode.NO_AUTH_ERROR);authorize(mutations.require(operation.getPictureId()),actor);try{return json.readValue(operation.getResultJson(),PictureVO.class);}catch(Exception e){throw new IllegalStateException("save result decoding failed");}}
    private void authorize(Picture p,User actor){if(p.getSpaceId()==null)throw new BusinessException(ErrorCode.PARAMS_ERROR,"协作保存仅支持团队图片");Space s=spaces.requirePermission(p.getSpaceId(),actor,SpacePermissions.EDIT);if(s.getSpaceType()!=1)throw new BusinessException(ErrorCode.PARAMS_ERROR,"协作保存仅支持团队图片");}
    private PictureVO replay(PictureEditSave operation,String fingerprint,User actor,Long id){if(!Objects.equals(operation.getUserId(),actor.getId())||!Objects.equals(operation.getPictureId(),id))throw new BusinessException(ErrorCode.NO_AUTH_ERROR);if(!Objects.equals(operation.getFingerprint(),fingerprint))throw new BusinessException(ErrorCode.CONFLICT_ERROR,"同一请求编号不能保存不同内容");try{return json.readValue(operation.getResultJson(),PictureVO.class);}catch(Exception e){throw new IllegalStateException("save result decoding failed");}}
    private void validateRequest(PictureSaveRequest r){if(r==null||r.getId()==null||r.getId()<=0||r.getExpectedRevision()==null||r.getExpectedRevision()<0||r.getRequestId()==null||!r.getRequestId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||r.getConnectionId()==null||r.getConnectionId().isEmpty()||r.getConnectionId().length()>128||r.getLockToken()==null||r.getLockToken().length()!=36||r.getEpoch()==null||r.getEpoch().length()!=36||r.getDraftVersion()==null||!r.getDraftVersion().matches("[0-9]{1,19}"))throw new BusinessException(ErrorCode.PARAMS_ERROR,"保存参数不完整");}
    private int[] pngDimensions(MultipartFile file){if(file==null||file.isEmpty()||file.getSize()>2*1024*1024||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".png"))throw new BusinessException(ErrorCode.PARAMS_ERROR,"编辑结果须为不超过 2MiB 的 PNG");try(InputStream source=file.getInputStream();MemoryCacheImageInputStream input=new MemoryCacheImageInputStream(source)){Iterator<ImageReader> readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片不可解析");ImageReader reader=readers.next();try{reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);if(!"png".equalsIgnoreCase(reader.getFormatName())||w<1||h<1||w>8192||h>8192||(long)w*h>16000000)throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片像素超出编辑限制");return new int[]{w,h};}finally{reader.dispose();}}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片不可解析");}}
    private void bounds(Picture p,PictureWriteGate.Snapshot state,PictureSaveRequest r,int[] actual){if(p.getPicWidth()==null||p.getPicHeight()==null||p.getPicWidth()<1||p.getPicHeight()<1)throw new BusinessException(ErrorCode.PARAMS_ERROR,"原图尺寸无效");boolean swapped=state.rotation==90||state.rotation==270;long w=Math.max(1,Math.round((swapped?p.getPicHeight():p.getPicWidth())*state.scale)),h=Math.max(1,Math.round((swapped?p.getPicWidth():p.getPicHeight())*state.scale));if(w>8192||h>8192||w*h>16000000)throw new BusinessException(ErrorCode.PARAMS_ERROR,"变换后图片过大");boolean none=r.getCropX()==null&&r.getCropY()==null&&r.getCropWidth()==null&&r.getCropHeight()==null;long x=none?0:required(r.getCropX()),y=none?0:required(r.getCropY()),cw=none?w:required(r.getCropWidth()),ch=none?h:required(r.getCropHeight());if(x<0||y<0||cw<1||ch<1||x+cw>w||y+ch>h||actual[0]!=cw||actual[1]!=ch)throw new BusinessException(ErrorCode.PARAMS_ERROR,"裁剪范围或导出尺寸不符");}
    private int required(Integer value){if(value==null)throw new BusinessException(ErrorCode.PARAMS_ERROR,"裁剪参数须完整");return value;}
    private String fingerprint(MultipartFile file,PictureSaveRequest r,User actor){try{Map<String,Object> values=new TreeMap<>();values.put("picture",r.getId());values.put("user",actor.getId());values.put("revision",r.getExpectedRevision());values.put("connection",r.getConnectionId());values.put("lockToken",r.getLockToken());values.put("epoch",r.getEpoch());values.put("draft",r.getDraftVersion());values.put("x",r.getCropX());values.put("y",r.getCropY());values.put("width",r.getCropWidth());values.put("height",r.getCropHeight());values.put("bytes",hex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())));return hex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(values)));}catch(Exception e){throw new BusinessException(ErrorCode.PARAMS_ERROR,"无法读取编辑结果");}}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    private static final class Commit{final PictureVO view;final Picture picture;final boolean replayed;Commit(PictureVO view,Picture picture,boolean replayed){this.view=view;this.picture=picture;this.replayed=replayed;}}
}
