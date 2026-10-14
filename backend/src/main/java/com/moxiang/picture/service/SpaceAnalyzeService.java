package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.mapper.PictureMapper;
import com.moxiang.picture.mapper.SpaceMapper;
import com.moxiang.picture.model.dto.space.SpaceAnalyzeRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.SpaceAnalyzeVO.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Restores the prototype's six analyses using existing picture and space tables. */
@Slf4j
@Service
public class SpaceAnalyzeService {
    private static final int TAG_BATCH_SIZE = 500;
    private final PictureMapper pictures;
    private final SpaceMapper spaceMapper;
    private final SpaceService spaces;
    private final UserService users;
    private final ObjectMapper json;

    public SpaceAnalyzeService(PictureMapper pictures, SpaceMapper spaceMapper,
                               SpaceService spaces, UserService users, ObjectMapper json) {
        this.pictures = pictures; this.spaceMapper = spaceMapper;
        this.spaces = spaces; this.users = users; this.json = json;
    }

    public Usage usage(SpaceAnalyzeRequest request, User user) {
        Space space = authorize(request, user);
        if (space != null) {
            return new Usage(value(space.getTotalSize()), value(space.getTotalCount()),
                    space.getMaxSize(), space.getMaxCount(),
                    percentage(value(space.getTotalSize()), space.getMaxSize()),
                    percentage(value(space.getTotalCount()), space.getMaxCount()));
        }
        QueryWrapper<Picture> query = scope(request).select(
                "COUNT(*) AS count", "COALESCE(SUM(picSize), 0) AS totalSize");
        Map<String, Object> row = first(pictures.selectMaps(query));
        return new Usage(number(row, "totalSize"), number(row, "count"), null, null, null, null);
    }

    public List<Category> category(SpaceAnalyzeRequest request, User user) {
        authorize(request, user);
        // NULL and blank categories form one visible bucket. Aggregation stays in MySQL.
        String expression = "COALESCE(NULLIF(TRIM(category), ''), '未分类')";
        QueryWrapper<Picture> query = scope(request).select(expression + " AS category",
                "COUNT(*) AS count", "COALESCE(SUM(picSize), 0) AS totalSize")
                .groupBy(expression).orderByDesc("count").orderByAsc("category");
        return pictures.selectMaps(query).stream().map(row -> new Category(
                String.valueOf(row.get("category")), number(row, "count"), number(row, "totalSize")))
                .collect(Collectors.toList());
    }

    public List<Tag> tag(SpaceAnalyzeRequest request, User user) {
        authorize(request, user);
        int topN = topN(request);
        long maxId = number(first(pictures.selectMaps(scope(request).select("MAX(id) AS maxId"))), "maxId");
        if (maxId <= 0) return Collections.emptyList();
        long cursor = 0, invalidRows = 0;
        Map<String, Long> counts = new HashMap<>();
        // Keyset batches avoid loading every picture's JSON at once. The upper ID fence
        // prevents continuous new uploads from extending this scan indefinitely.
        while (cursor < maxId) {
            List<Picture> batch = pictures.selectList(scope(request).select("id", "tags")
                    .gt("id", cursor).le("id", maxId).orderByAsc("id").last("LIMIT " + TAG_BATCH_SIZE));
            if (batch.isEmpty()) break;
            for (Picture picture : batch) {
                if (picture.getTags() == null || picture.getTags().trim().isEmpty()) continue;
                try {
                    JsonNode tags = json.readTree(picture.getTags());
                    if (tags == null || !tags.isArray()) { invalidRows++; continue; }
                    Set<String> unique = new HashSet<>();
                    for (JsonNode node : tags) {
                        if (node.isTextual() && !node.asText().trim().isEmpty()) unique.add(node.asText().trim());
                    }
                    // A repeated tag in one legacy row still counts as one associated picture.
                    for (String tag : unique) counts.merge(tag, 1L, Long::sum);
                } catch (java.io.IOException e) { invalidRows++; }
            }
            long next = batch.get(batch.size() - 1).getId();
            ThrowUtils.throwIf(next <= cursor, ErrorCode.SYSTEM_ERROR, "标签分析游标未前进");
            cursor = next;
            if (batch.size() < TAG_BATCH_SIZE) break;
        }
        if (invalidRows > 0) log.warn("标签分析跳过 {} 条非 JSON 数组记录", invalidRows);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(topN).map(entry -> new Tag(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    public List<SizeBucket> size(SpaceAnalyzeRequest request, User user) {
        authorize(request, user);
        QueryWrapper<Picture> query = scope(request).select(
                "COALESCE(SUM(CASE WHEN picSize >= 0 AND picSize < 102400 THEN 1 ELSE 0 END), 0) AS small",
                "COALESCE(SUM(CASE WHEN picSize >= 102400 AND picSize < 512000 THEN 1 ELSE 0 END), 0) AS medium",
                "COALESCE(SUM(CASE WHEN picSize >= 512000 AND picSize < 1048576 THEN 1 ELSE 0 END), 0) AS large",
                "COALESCE(SUM(CASE WHEN picSize >= 1048576 THEN 1 ELSE 0 END), 0) AS huge");
        Map<String, Object> row = first(pictures.selectMaps(query));
        return Arrays.asList(new SizeBucket("<100KB", number(row, "small")),
                new SizeBucket("100KB-500KB", number(row, "medium")),
                new SizeBucket("500KB-1MB", number(row, "large")),
                new SizeBucket("≥1MB", number(row, "huge")));
    }

    public List<UserActivity> user(SpaceAnalyzeRequest request, User user) {
        authorize(request, user);
        ThrowUtils.throwIf(request.getUserId() != null && request.getUserId() <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 无效");
        String expression;
        // SQL identifiers/formats only come from this whitelist, never client strings.
        switch (request.getTimeDimension() == null ? "" : request.getTimeDimension()) {
            case "day": expression = "DATE_FORMAT(createTime, '%Y-%m-%d')"; break;
            case "week": expression = "DATE_FORMAT(createTime, '%x-W%v')"; break;
            case "month": expression = "DATE_FORMAT(createTime, '%Y-%m')"; break;
            default: throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间维度仅支持 day/week/month");
        }
        LocalDate start = request.getStartDate(), end = request.getEndDate();
        checkDate(start); checkDate(end);
        ThrowUtils.throwIf(start != null && end != null && (start.isAfter(end)
                || ChronoUnit.DAYS.between(start, end) > 3660), ErrorCode.PARAMS_ERROR, "日期范围无效或超过十年");
        QueryWrapper<Picture> query = scope(request).select(expression + " AS period", "COUNT(*) AS count")
                .eq(request.getUserId() != null, "userId", request.getUserId()).isNotNull("createTime");
        if (start != null) query.ge("createTime", start.atStartOfDay());
        if (end != null) query.lt("createTime", end.plusDays(1).atStartOfDay());
        query.groupBy(expression).orderByAsc("period");
        return pictures.selectMaps(query).stream().map(row -> new UserActivity(
                String.valueOf(row.get("period")), number(row, "count"))).collect(Collectors.toList());
    }

    public List<Rank> rank(SpaceAnalyzeRequest request, User user) {
        requireLogin(user);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!users.isAdmin(user), ErrorCode.NO_AUTH_ERROR, "空间排行仅系统管理员可查看");
        int limit = topN(request);
        return spaceMapper.selectList(new QueryWrapper<Space>()
                .select("id", "spaceName", "userId", "totalSize", "totalCount")
                .orderByDesc("totalSize").orderByAsc("id").last("LIMIT " + limit))
                .stream().map(space -> new Rank(space.getId(), space.getSpaceName(), space.getUserId(),
                        value(space.getTotalSize()), value(space.getTotalCount()))).collect(Collectors.toList());
    }

    private Space authorize(SpaceAnalyzeRequest request, User user) {
        requireLogin(user);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        int scopes = (request.isQueryAll() ? 1 : 0) + (request.isQueryPublic() ? 1 : 0) + (request.getSpaceId() != null ? 1 : 0);
        ThrowUtils.throwIf(scopes != 1 || (request.getSpaceId() != null && request.getSpaceId() <= 0),
                ErrorCode.PARAMS_ERROR, "请且仅指定一个分析范围");
        if (request.isQueryAll() || request.isQueryPublic()) {
            ThrowUtils.throwIf(!users.isAdmin(user), ErrorCode.NO_AUTH_ERROR, "公共与全站分析仅系统管理员可查看");
            return null;
        }
        // Personal owner/system admin; for a team, a team admin/system admin.
        return spaces.requirePermission(request.getSpaceId(), user, SpacePermissions.MANAGE);
    }

    private QueryWrapper<Picture> scope(SpaceAnalyzeRequest request) {
        QueryWrapper<Picture> query = new QueryWrapper<>();
        if (request.isQueryPublic()) query.isNull("spaceId");
        else if (!request.isQueryAll()) query.eq("spaceId", request.getSpaceId());
        // Picture.@TableLogic adds isDelete=0 in MyBatis-Plus generated SELECTs.
        return query;
    }
    private int topN(SpaceAnalyzeRequest request) {
        Integer count = request.getTopN();
        ThrowUtils.throwIf(count == null || count < 1 || count > 50, ErrorCode.PARAMS_ERROR, "topN 必须为 1—50");
        return count;
    }
    private static void requireLogin(User user) {
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
    }
    private static void checkDate(LocalDate date) {
        ThrowUtils.throwIf(date != null && (date.getYear() < 1970 || date.getYear() > 2100), ErrorCode.PARAMS_ERROR, "日期须在 1970—2100 年内");
    }
    private static long value(Long number) { return number == null ? 0 : number; }
    private static Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }
    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }
    private static Double percentage(long used, Long maximum) {
        if (maximum == null || maximum <= 0) return null;
        return BigDecimal.valueOf(used).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maximum), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
