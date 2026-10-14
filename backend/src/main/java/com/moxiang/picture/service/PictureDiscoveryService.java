package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.search.*;
import com.moxiang.picture.mapper.PictureMapper;
import com.moxiang.picture.model.dto.picture.PictureSearchRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.PictureVO;
import com.moxiang.picture.model.vo.PictureSearchVO.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PictureDiscoveryService {
    private final PictureMapper pictures;
    private final SpaceService spaces;
    private final UserService users;
    private final ImageSearchGateway gateway;
    public PictureDiscoveryService(PictureMapper pictures,SpaceService spaces,UserService users,ImageSearchGateway gateway){this.pictures=pictures;this.spaces=spaces;this.users=users;this.gateway=gateway;}

    public ExternalResult byPicture(PictureSearchRequest request,User actor){
        login(actor);
        ThrowUtils.throwIf(request==null||request.getPictureId()==null||request.getPictureId()<=0,ErrorCode.PARAMS_ERROR);
        int limit=limit(request);
        Picture source=pictures.selectById(request.getPictureId());
        ThrowUtils.throwIf(source==null,ErrorCode.NOT_FOUND_ERROR);
        // External providers receive only already-public approved material.
        ThrowUtils.throwIf(source.getSpaceId()!=null||!Integer.valueOf(1).equals(source.getReviewStatus()),ErrorCode.NO_AUTH_ERROR,"仅审核通过的公共图片支持公网以图搜图");
        try{URI uri=URI.create(source.getUrl());ThrowUtils.throwIf(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null,ErrorCode.PARAMS_ERROR,"源图片须为可访问的 HTTPS 图片");}
        catch(IllegalArgumentException|NullPointerException e){throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片地址无效");}
        return gateway.search(searchSourceUrl(source),limit);
    }

    /** Prefer the original PNG/JPEG over the WebP display derivative. */
    public static String searchSourceUrl(Picture picture){
        String key=picture.getOriginalKey();
        if(key!=null&&key.matches("public/[1-9][0-9]{0,18}/[A-Za-z0-9_./-]{1,300}\\.(?i:png|jpe?g)")&&!key.contains("..")){
            URI display=URI.create(picture.getUrl());return display.resolve("/"+key).toString();
        }
        URI display=URI.create(picture.getUrl());
        if(display.getPath()!=null&&display.getPath().toLowerCase(Locale.ROOT).endsWith(".webp")){
            return picture.getUrl()+(display.getRawQuery()==null?"?":"&")+"imageMogr2/format/png";
        }
        return picture.getUrl();
    }

    public List<ColorMatch> byColor(PictureSearchRequest request,User actor){
        login(actor);ThrowUtils.throwIf(request==null,ErrorCode.PARAMS_ERROR);
        int target=ColorSimilarity.parse(request.getPicColor()),limit=limit(request);
        Double minimum=request.getMinSimilarity();
        ThrowUtils.throwIf(minimum==null||!Double.isFinite(minimum)||minimum<0||minimum>1,ErrorCode.PARAMS_ERROR,"相似度阈值须为0—1");
        authorize(request.getSpaceId(),actor);
        List<Picture> candidates=pictures.selectList(scope(request.getSpaceId()).select("id","picColor")
                .isNotNull("picColor").orderByAsc("id").last("LIMIT 10001"));
        ThrowUtils.throwIf(candidates.size()>10000,ErrorCode.PARAMS_ERROR,"颜色候选超过10000张，请缩小到指定空间检索");
        List<Scored> scored=new ArrayList<>();
        for(Picture candidate:candidates){
            try{double similarity=ColorSimilarity.similarity(target,ColorSimilarity.parse(candidate.getPicColor()));if(similarity>=minimum)scored.add(new Scored(candidate.getId(),similarity));}
            catch(BusinessException invalidLegacyColor){/* A malformed historical color does not fail the whole query. */}
        }
        scored.sort(Comparator.comparingDouble((Scored row)->row.score).reversed().thenComparing(row->row.id));
        if(scored.isEmpty())return Collections.emptyList();
        List<Scored> top=scored.subList(0,Math.min(limit,scored.size()));
        List<Long> ids=top.stream().map(row->row.id).collect(Collectors.toList());
        // Reapply scope/review filters instead of trusting an earlier candidate snapshot.
        Map<Long,Picture> found=pictures.selectList(scope(request.getSpaceId()).in("id",ids)).stream()
                .collect(Collectors.toMap(Picture::getId,picture->picture));
        authorize(request.getSpaceId(),actor);
        List<ColorMatch> result=new ArrayList<>();
        for(Scored score:top){Picture picture=found.get(score.id);if(picture==null)continue;ColorMatch match=new ColorMatch();BeanUtils.copyProperties(PictureVO.objToVo(picture),match);match.setSimilarity(score.score);result.add(match);}
        return result;
    }
    private QueryWrapper<Picture> scope(Long spaceId){QueryWrapper<Picture> query=new QueryWrapper<>();return spaceId==null?query.isNull("spaceId").eq("reviewStatus",1):query.eq("spaceId",spaceId);}
    private void authorize(Long spaceId,User actor){
        User fresh=users.getById(actor.getId());ThrowUtils.throwIf(fresh==null,ErrorCode.NOT_LOGIN_ERROR);
        if(spaceId!=null){ThrowUtils.throwIf(spaceId<=0,ErrorCode.PARAMS_ERROR);spaces.authorized(spaceId,fresh);}
    }
    private int limit(PictureSearchRequest request){Integer count=request.getTopN();ThrowUtils.throwIf(count==null||count<1||count>50,ErrorCode.PARAMS_ERROR,"topN须为1—50");return count;}
    private void login(User actor){ThrowUtils.throwIf(actor==null||actor.getId()==null,ErrorCode.NOT_LOGIN_ERROR);}
    private static final class Scored{final Long id;final double score;Scored(Long id,double score){this.id=id;this.score=score;}}
}
