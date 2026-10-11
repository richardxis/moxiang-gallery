package com.moxiang.picture.manager.cache;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.exception.ErrorCode;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import org.springframework.beans.BeanUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** 规范化对象同时用于查询和键计算，确保两者的条件语义一致。 */
public final class PublicPictureQuery {
    private PublicPictureQuery() { }
    public static PictureQueryRequest normalize(PictureQueryRequest input) {
        if (input == null || input.getCurrent() <= 0 || input.getPageSize() <= 0 || input.getPageSize() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数无效");
        }
        if (input.getSpaceId() != null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "公共图库接口不支持空间查询");
        PictureQueryRequest query = new PictureQueryRequest();
        BeanUtils.copyProperties(input, query);
        query.setName(trim(query.getName()));
        query.setIntroduction(trim(query.getIntroduction()));
        query.setCategory(trim(query.getCategory()));
        query.setPicFormat(trim(query.getPicFormat()));
        query.setSearchText(trim(query.getSearchText()));
        query.setReviewMessage(trim(query.getReviewMessage()));
        if (query.getTags() != null) {
            List<String> tags = query.getTags().stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).distinct().sorted().collect(Collectors.toList());
            query.setTags(tags.isEmpty() ? null : tags);
        }
        query.setReviewStatus(1);
        query.setNullSpaceId(true);
        if (StrUtil.isBlank(query.getSortField())) {
            query.setSortField("createTime");
            query.setSortOrder("descend");
        }
        if (!Arrays.asList("id", "createTime", "editTime", "updateTime", "picSize", "picWidth", "picHeight")
                .contains(query.getSortField()) || !("ascend".equals(query.getSortOrder()) || "descend".equals(query.getSortOrder()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序参数不支持");
        }
        return query;
    }
    public static String key(PictureQueryRequest normalized, String version, ObjectMapper mapper) {
        return key(normalized, version, mapper, "moxiang:picture");
    }
    public static String key(PictureQueryRequest normalized, String version, ObjectMapper mapper, String namespace) {
        try {
            Map<String, Object> values = mapper.convertValue(normalized, new TypeReference<TreeMap<String, Object>>() { });
            values.values().removeIf(Objects::isNull);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(values).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 255));
            return namespace + ":public:page:v2:" + version + ":" + hex;
        } catch (Exception e) { throw new IllegalStateException("无法构造缓存键", e); }
    }
    private static String trim(String value) { return StrUtil.isBlank(value) ? null : value.trim(); }
}
