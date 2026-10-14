package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.space.SpaceAnalyzeRequest;
import com.moxiang.picture.model.entity.User;
import com.moxiang.picture.model.vo.SpaceAnalyzeVO.*;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Explicit opt-in, real MySQL SELECT-only verification. No credential or row content is logged. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpaceAnalyzeMySqlReadOnlyTest {
    private SqlSession session;
    private Connection connection;
    private SpaceAnalyzeService service;
    private User admin;
    private long publicCount, totalCount;
    @BeforeAll void connect() throws Exception {
        String config = System.getProperty("moxiang.analysis.mysql.config");
        assumeTrue(config != null && !config.isEmpty(), "Supply an explicit private YAML path to run real MySQL SELECT checks");
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean(); yaml.setResources(new FileSystemResource(config));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new PropertiesPropertySource("private-config", yaml.getObject()));
        String url = environment.getRequiredProperty("spring.datasource.url");
        assertTrue(url.startsWith("jdbc:mysql://"), "Only MySQL is accepted by this opt-in test");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                environment.getRequiredProperty("spring.datasource.username"), environment.getProperty("spring.datasource.password", ""));
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("connectTimeout", "2000"); connectionProperties.setProperty("socketTimeout", "5000");
        dataSource.setConnectionProperties(connectionProperties);
        connection = dataSource.getConnection(); connection.setReadOnly(true); connection.setAutoCommit(false);
        assertTrue(connection.isReadOnly());
        MybatisConfiguration configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(false);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setEnvironment(new Environment("mysql-read-only", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(PictureMapper.class); configuration.addMapper(SpaceMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
        UserService users = mock(UserService.class); admin = new User(); admin.setId(1L); when(users.isAdmin(admin)).thenReturn(true);
        service = new SpaceAnalyzeService(session.getMapper(PictureMapper.class), session.getMapper(SpaceMapper.class),
                mock(SpaceService.class), users, new ObjectMapper());
        publicCount = scalar("SELECT COUNT(*) FROM picture WHERE isDelete=0 AND spaceId IS NULL");
        totalCount = scalar("SELECT COUNT(*) FROM picture WHERE isDelete=0");
    }
    @AfterAll void close() throws Exception {
        if (connection != null) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("scope", "real MySQL read-only aggregation checks; no HTTP latency or cache benchmark");
            evidence.put("time", java.time.Instant.now().toString());
            evidence.put("activePublicPictures", publicCount); evidence.put("activeTotalPictures", totalCount);
            Path output = Paths.get("target", "analysis-mysql-readonly.json"); Files.createDirectories(output.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), evidence);
            connection.rollback();
        }
        if (session != null) session.close();
        else if (connection != null) connection.close();
    }
    private SpaceAnalyzeRequest publicScope() { SpaceAnalyzeRequest request = new SpaceAnalyzeRequest(); request.setQueryPublic(true); return request; }
    @Test void usageMatchesIndependentCountAndSum() throws Exception {
        Usage usage = service.usage(publicScope(), admin); assertEquals(publicCount, usage.getUsedCount());
        assertEquals(scalar("SELECT COALESCE(SUM(picSize),0) FROM picture WHERE isDelete=0 AND spaceId IS NULL"), usage.getUsedSize());
    }
    @Test void categoriesCoverExactlyTheActivePublicRows() {
        assertEquals(publicCount, service.category(publicScope(), admin).stream().mapToLong(Category::getCount).sum());
    }
    @Test void sizeBucketsCoverKnownNonnegativeSizes() throws Exception {
        assertEquals(scalar("SELECT COUNT(*) FROM picture WHERE isDelete=0 AND spaceId IS NULL AND picSize>=0"),
                service.size(publicScope(), admin).stream().mapToLong(SizeBucket::getCount).sum());
    }
    @Test void tagResultsAreUniquePositiveAndBounded() {
        List<Tag> tags = service.tag(publicScope(), admin);
        assertTrue(tags.size() <= 10); Set<String> seen = new HashSet<>();
        for (Tag tag : tags) { assertTrue(seen.add(tag.getTag())); assertTrue(tag.getCount()>0 && tag.getCount()<=publicCount); }
    }
    @Test void dayWeekAndMonthExecuteInMySqlAndPreserveTotal() throws Exception {
        long expected = scalar("SELECT COUNT(*) FROM picture WHERE isDelete=0 AND spaceId IS NULL AND createTime IS NOT NULL");
        for (String dimension : Arrays.asList("day", "week", "month")) {
            SpaceAnalyzeRequest request = publicScope(); request.setTimeDimension(dimension);
            List<UserActivity> result = service.user(request, admin);
            assertEquals(expected, result.stream().mapToLong(UserActivity::getCount).sum());
            if ("week".equals(dimension)) for (UserActivity row : result) assertTrue(row.getPeriod().matches("\\d{4}-W\\d{2}"));
        }
    }
    @Test void allScopeAndRankingMatchDatabaseOrder() throws Exception {
        SpaceAnalyzeRequest request = new SpaceAnalyzeRequest(); request.setQueryAll(true); request.setTopN(5);
        assertEquals(totalCount, service.usage(request, admin).getUsedCount());
        List<Rank> actual = service.rank(request, admin);
        try (Statement sql = connection.createStatement(); ResultSet expected = sql.executeQuery("SELECT id FROM space ORDER BY totalSize DESC,id ASC LIMIT 5")) {
            int i=0; while (expected.next()) assertEquals(expected.getLong(1), actual.get(i++).getId()); assertEquals(i, actual.size());
        }
    }
    private long scalar(String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }
}
