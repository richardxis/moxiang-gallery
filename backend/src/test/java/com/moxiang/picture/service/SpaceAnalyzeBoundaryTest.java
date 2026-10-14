package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.*;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.space.SpaceAnalyzeRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.SpaceAnalyzeVO.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SpaceAnalyzeBoundaryTest {
    PictureMapper pictures;
    SpaceMapper mapper;
    SpaceService spaces;
    UserService users;
    SpaceAnalyzeService service;
    User user;
    Space space;
    SpaceAnalyzeRequest request;
    @BeforeEach void setup() {
        pictures = mock(PictureMapper.class); mapper = mock(SpaceMapper.class);
        spaces = mock(SpaceService.class); users = mock(UserService.class);
        service = new SpaceAnalyzeService(pictures, mapper, spaces, users, new ObjectMapper());
        user = new User(); user.setId(7L);
        space = new Space(); space.setId(10L); space.setMaxCount(3L); space.setTotalCount(1L);
        space.setMaxSize(100L); space.setTotalSize(25L);
        when(spaces.requirePermission(10L, user, SpacePermissions.MANAGE)).thenReturn(space);
        request = new SpaceAnalyzeRequest(); request.setSpaceId(10L);
    }
    @Test void requiresLoginBeforeQueries() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.usage(request, null));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), error.getCode()); verifyNoInteractions(pictures, spaces);
    }
    @Test void rejectsNullRequest() { assertThrows(BusinessException.class, () -> service.usage(null, user)); }
    @Test void rejectsMissingScopeInsteadOfQueryingAll() {
        assertThrows(BusinessException.class, () -> service.category(new SpaceAnalyzeRequest(), user)); verifyNoInteractions(pictures);
    }
    @Test void rejectsAmbiguousScopesEvenForAdmin() {
        request.setQueryAll(true); when(users.isAdmin(user)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.category(request, user)); verifyNoInteractions(pictures);
    }
    @Test void rejectsBothGlobalFlags() {
        request.setSpaceId(null); request.setQueryAll(true); request.setQueryPublic(true);
        assertThrows(BusinessException.class, () -> service.usage(request, user));
    }
    @ParameterizedTest @ValueSource(longs = {0, -1}) void rejectsInvalidSpaceId(long id) {
        request.setSpaceId(id); assertThrows(BusinessException.class, () -> service.usage(request, user));
    }
    @Test void regularUserCannotAnalyzePublic() {
        request.setSpaceId(null); request.setQueryPublic(true);
        assertThrows(BusinessException.class, () -> service.category(request, user)); verifyNoInteractions(pictures);
    }
    @Test void regularUserCannotAnalyzeAll() {
        request.setSpaceId(null); request.setQueryAll(true);
        assertThrows(BusinessException.class, () -> service.usage(request, user)); verifyNoInteractions(pictures);
    }
    @Test void teamMemberWithoutManagementPermissionCannotReadAggregates() {
        when(spaces.requirePermission(10L, user, SpacePermissions.MANAGE)).thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR));
        assertThrows(BusinessException.class, () -> service.category(request, user)); verifyNoInteractions(pictures);
    }
    @Test void usesSpaceCounterAndRoundsPercentages() {
        Usage result = service.usage(request, user);
        assertEquals(25L, result.getUsedSize()); assertEquals(25.0, result.getSizeUsageRatio());
        assertEquals(33.33, result.getCountUsageRatio()); verifyNoInteractions(pictures);
    }
    @Test void handlesZeroOrMissingQuota() {
        space.setMaxSize(0L); space.setMaxCount(null);
        Usage result = service.usage(request, user); assertNull(result.getSizeUsageRatio()); assertNull(result.getCountUsageRatio());
    }
    @Test void publicAggregationUsesNullSpaceAndNoQuota() {
        request.setSpaceId(null); request.setQueryPublic(true); when(users.isAdmin(user)).thenReturn(true);
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(row("count", 3, "totalSize", new BigDecimal("1024"))));
        Usage result = service.usage(request, user); assertEquals(3, result.getUsedCount()); assertEquals(1024, result.getUsedSize()); assertNull(result.getMaxSize());
        ArgumentCaptor<QueryWrapper<Picture>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(pictures).selectMaps(query.capture()); assertTrue(query.getValue().getSqlSegment().contains("spaceId IS NULL"));
        assertTrue(query.getValue().getSqlSelect().contains("SUM(picSize)"));
    }
    @Test void categoriesMapNumericDriverTypes() {
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(row("category", "素材", "count", 2, "totalSize", new BigDecimal("12"))));
        Category result = service.category(request, user).get(0); assertEquals(2, result.getCount()); assertEquals(12, result.getTotalSize());
    }
    @Test void emptySizeAnalysisStillReturnsAllFourBuckets() {
        when(pictures.selectMaps(any())).thenReturn(Collections.emptyList());
        List<SizeBucket> result = service.size(request, user); assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(bucket -> bucket.getCount() == 0)); assertEquals("≥1MB", result.get(3).getSizeRange());
    }
    @Test void tagsSkipMalformedJsonAndDeduplicateWithinOnePicture() {
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(row("maxId", 9L)));
        when(pictures.selectList(any())).thenReturn(Arrays.asList(picture(1, "[\"A\",\"A\",\" B \",null,5]"),
                picture(2, "not-json"), picture(3, "[\"A\"]"), picture(4, "{\"key\":1}"), picture(9, null)));
        List<Tag> result = service.tag(request, user);
        assertEquals(2, result.size()); assertEquals("A", result.get(0).getTag()); assertEquals(2, result.get(0).getCount());
        assertEquals("B", result.get(1).getTag());
    }
    @Test void tagScanMovesCursorAfterFullBatchAndStopsAtInitialUpperId() {
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(row("maxId", 501L)));
        List<Picture> first = new ArrayList<>(); for (long id = 1; id <= 500; id++) first.add(picture(id, "[\"A\"]"));
        when(pictures.selectList(any())).thenReturn(first, Collections.singletonList(picture(501, "[\"B\"]")));
        List<Tag> result = service.tag(request, user); assertEquals(500, result.get(0).getCount());
        ArgumentCaptor<QueryWrapper<Picture>> queries = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(pictures, times(2)).selectList(queries.capture());
        assertTrue(queries.getAllValues().get(1).getSqlSegment().contains("LIMIT 500"));
        assertTrue(queries.getAllValues().get(1).getParamNameValuePairs().containsValue(500L));
        assertTrue(queries.getAllValues().get(1).getParamNameValuePairs().containsValue(501L));
    }
    @Test void emptyTagScopeReturnsImmediately() {
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(row("maxId", null)));
        assertTrue(service.tag(request, user).isEmpty()); verify(pictures, never()).selectList(any());
    }
    @ParameterizedTest @ValueSource(strings = {"day", "week", "month"}) void allowsOnlyKnownTimeFormats(String format) {
        request.setTimeDimension(format);
        when(pictures.selectMaps(any())).thenReturn(Collections.singletonList(row("period", "2026-01", "count", 2)));
        assertEquals(2, service.user(request, user).get(0).getCount());
        ArgumentCaptor<QueryWrapper<Picture>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(pictures).selectMaps(query.capture()); assertTrue(query.getValue().getSqlSelect().contains("DATE_FORMAT(createTime"));
        if ("week".equals(format)) assertTrue(query.getValue().getSqlSelect().contains("%x-W%v"));
    }
    @Test void rejectsSqlExpressionAsTimeDimension() {
        request.setTimeDimension("day'); DROP TABLE picture; --");
        assertThrows(BusinessException.class, () -> service.user(request, user)); verifyNoInteractions(pictures);
    }
    @Test void rejectsNullTimeDimension() {
        request.setTimeDimension(null); assertThrows(BusinessException.class, () -> service.user(request, user));
    }
    @Test void rejectsInvertedDateRange() {
        request.setStartDate(LocalDate.of(2026, 2, 2)); request.setEndDate(LocalDate.of(2026, 2, 1));
        assertThrows(BusinessException.class, () -> service.user(request, user));
    }
    @Test void rejectsOutOfRangeDate() {
        request.setEndDate(LocalDate.MAX); assertThrows(BusinessException.class, () -> service.user(request, user));
    }
    @Test void endDateIsExclusiveFollowingMidnightAndUserIdIsBound() {
        request.setUserId(7L); request.setEndDate(LocalDate.of(2026, 2, 1));
        when(pictures.selectMaps(any())).thenReturn(Collections.emptyList()); service.user(request, user);
        ArgumentCaptor<QueryWrapper<Picture>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(pictures).selectMaps(query.capture()); query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(LocalDate.of(2026, 2, 2).atStartOfDay()));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
    }
    @Test void rejectsNonpositiveUserId() {
        request.setUserId(0L); assertThrows(BusinessException.class, () -> service.user(request, user));
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 51}) void topNIsBounded(int count) {
        request.setTopN(count); when(users.isAdmin(user)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.rank(request, user)); verifyNoInteractions(mapper);
    }
    @Test void rankRejectsNonAdminBeforeQuery() {
        assertThrows(BusinessException.class, () -> service.rank(request, user)); verifyNoInteractions(mapper);
    }
    @Test void rankHasDeterministicOrderAndLimitedColumns() {
        when(users.isAdmin(user)).thenReturn(true); when(mapper.selectList(any())).thenReturn(Collections.singletonList(space));
        request.setTopN(5); assertEquals(25, service.rank(request, user).get(0).getTotalSize());
        ArgumentCaptor<QueryWrapper<Space>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(query.capture()); String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("ORDER BY totalSize DESC,id ASC")); assertTrue(sql.contains("LIMIT 5"));
    }
    private static Picture picture(long id, String tags) { Picture picture = new Picture(); picture.setId(id); picture.setTags(tags); return picture; }
    private static Map<String, Object> row(Object... items) {
        Map<String, Object> result = new HashMap<>(); for (int i = 0; i < items.length; i += 2) result.put((String)items[i], items[i + 1]); return result;
    }
}
