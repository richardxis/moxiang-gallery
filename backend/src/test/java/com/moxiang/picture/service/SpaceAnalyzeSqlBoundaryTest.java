package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.manager.auth.SpacePermissions;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.space.SpaceAnalyzeRequest;
import com.moxiang.picture.model.entity.*;
import com.moxiang.picture.model.vo.SpaceAnalyzeVO.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes generated MyBatis-Plus SQL in isolated H2 MySQL mode, not production MySQL. */
class SpaceAnalyzeSqlBoundaryTest {
    SqlSession session;
    SpaceAnalyzeService service;
    SpaceAnalyzeRequest request;
    User admin;
    @BeforeEach void setup() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:analysis_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE picture(id BIGINT PRIMARY KEY, spaceId BIGINT, category VARCHAR(128), tags VARCHAR(512), picSize BIGINT, userId BIGINT, createTime TIMESTAMP, isDelete INT DEFAULT 0)");
            sql.execute("CREATE TABLE space(id BIGINT PRIMARY KEY, spaceName VARCHAR(128), userId BIGINT, totalSize BIGINT, totalCount BIGINT)");
            sql.execute("INSERT INTO picture VALUES (1,NULL,'素材','[\"A\",\"A\"]',102399,7,'2026-01-01 10:00:00',0),"
                    + "(2,NULL,'素材','[\"B\",\"A\"]',102400,8,'2026-01-01 23:59:59',0),"
                    + "(3,NULL,' ','[]',512000,7,'2026-01-02 00:00:00',0),"
                    + "(4,10,'团队','[\"private\"]',1048576,7,'2026-01-02 10:00:00',0),"
                    + "(5,NULL,'deleted','[\"deleted\"]',9,7,'2026-01-01 00:00:00',1),"
                    + "(6,NULL,NULL,NULL,NULL,7,'2026-01-01 00:00:00',0)");
            sql.execute("INSERT INTO space VALUES(10,'A',7,200,2),(11,'B',8,500,3),(12,'C',9,500,4)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(PictureMapper.class); configuration.addMapper(SpaceMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        UserService users = mock(UserService.class); SpaceService spaces = mock(SpaceService.class);
        admin = new User(); admin.setId(7L); when(users.isAdmin(admin)).thenReturn(true);
        Space space = new Space(); space.setId(10L); when(spaces.requirePermission(10L, admin, SpacePermissions.MANAGE)).thenReturn(space);
        service = new SpaceAnalyzeService(session.getMapper(PictureMapper.class), session.getMapper(SpaceMapper.class), spaces, users, new ObjectMapper());
        request = new SpaceAnalyzeRequest(); request.setQueryPublic(true);
    }
    @AfterEach void close() throws Exception {
        if (session != null) { try (Statement sql = session.getConnection().createStatement()) { sql.execute("DROP ALL OBJECTS"); } session.close(); }
    }
    @Test void publicSumExcludesPrivateAndLogicallyDeletedRowsAndHandlesNullSize() {
        Usage usage = service.usage(request, admin); assertEquals(4, usage.getUsedCount()); assertEquals(716799, usage.getUsedSize());
    }
    @Test void categoryGroupsBlankAndNullAndExcludesDeleted() {
        List<Category> result = service.category(request, admin); assertEquals(2, result.size());
        assertEquals(4, result.stream().mapToLong(Category::getCount).sum());
        Category missing = result.stream().filter(row -> "未分类".equals(row.getCategory())).findFirst().get(); assertEquals(2, missing.getCount());
    }
    @Test void sizeBucketsUseNonoverlappingExactBoundaries() {
        List<SizeBucket> result = service.size(request, admin);
        assertEquals(Arrays.asList(1L,1L,1L,0L), Arrays.asList(result.get(0).getCount(),result.get(1).getCount(),result.get(2).getCount(),result.get(3).getCount()));
        request.setQueryPublic(false); request.setSpaceId(10L);
        assertEquals(1, service.size(request, admin).get(3).getCount());
    }
    @Test void tagScanExecutesWithScopeAndLogicDeleteFilters() {
        List<Tag> result = service.tag(request, admin); assertEquals(2, result.size()); assertEquals("A", result.get(0).getTag()); assertEquals(2, result.get(0).getCount());
    }
    @Test void allScopeIncludesTeamAndExcludesDeleted() {
        request.setQueryPublic(false); request.setQueryAll(true); assertEquals(5, service.usage(request, admin).getUsedCount());
    }
    @Test void rankingHasStableTieBreakAndTopN() {
        request.setTopN(2); List<Rank> result = service.rank(request, admin);
        assertEquals(2, result.size()); assertEquals(11L, result.get(0).getId()); assertEquals(12L, result.get(1).getId());
    }
}
