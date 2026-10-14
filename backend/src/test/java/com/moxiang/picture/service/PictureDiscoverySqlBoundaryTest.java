package com.moxiang.picture.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.manager.cache.PublicPictureCache;
import com.moxiang.picture.manager.search.ImageSearchGateway;
import com.moxiang.picture.manager.write.PictureWriteGate;
import com.moxiang.picture.mapper.*;
import com.moxiang.picture.model.dto.picture.*;
import com.moxiang.picture.model.entity.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PictureDiscoverySqlBoundaryTest {
    JdbcDataSource dataSource;SqlSession session;PictureMapper pictures;SpaceMapper rows;UserService users;User actor;PictureDiscoveryService discovery;PictureBatchEditService batch;
    @BeforeEach void setup()throws Exception{
        dataSource=new JdbcDataSource();dataSource.setURL("jdbc:h2:mem:picture_tools_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        try(Connection connection=dataSource.getConnection();Statement sql=connection.createStatement()){
            List<String> columns=new ArrayList<>();
            for(java.lang.reflect.Field field:Picture.class.getDeclaredFields()){
                if(Modifier.isStatic(field.getModifiers()))continue;
                Class<?> type=field.getType();String sqlType=type==Long.class?"BIGINT":type==Integer.class?"INT":type==Double.class?"DOUBLE":type==java.util.Date.class?"TIMESTAMP":"VARCHAR(2048)";
                columns.add(field.getName()+" "+sqlType+("id".equals(field.getName())?" PRIMARY KEY":""));
            }
            sql.execute("CREATE TABLE picture("+String.join(",",columns)+")");
            sql.execute("CREATE TABLE space(id BIGINT PRIMARY KEY,spaceName VARCHAR(128))");sql.execute("INSERT INTO space VALUES(10,'test')");
            sql.execute("INSERT INTO picture(id,spaceId,picColor,name,tags,revision,reviewStatus,isDelete) VALUES(1,NULL,'#FF0000','red','[]',0,1,0),(2,NULL,'#00FF00','green','[]',0,1,0),(3,10,'#FF0000','private','[]',0,1,0),(4,NULL,'#FF0000','deleted','[]',0,1,1),(5,NULL,'#FF0000','pending','[]',0,0,0)");
        }
        MybatisConfiguration configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(false);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),dataSource));configuration.addMapper(PictureMapper.class);configuration.addMapper(SpaceMapper.class);
        session=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));pictures=session.getMapper(PictureMapper.class);rows=session.getMapper(SpaceMapper.class);
        users=mock(UserService.class);actor=new User();actor.setId(7L);when(users.getById(7L)).thenReturn(actor);when(users.isAdmin(actor)).thenReturn(true);
        discovery=new PictureDiscoveryService(pictures,mock(SpaceService.class),users,mock(ImageSearchGateway.class));
        // SqlSessionTemplate joins the Spring-managed transaction for all mapper calls.
        TransactionTemplate transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        batch=new PictureBatchEditService(pictures,rows,mock(SpaceService.class),users,new PictureWriteGate(),transaction,mock(PublicPictureCache.class),new ObjectMapper());
    }
    @AfterEach void close()throws Exception{try(Connection c=dataSource.getConnection();Statement sql=c.createStatement()){sql.execute("DROP ALL OBJECTS");}}
    @Test void colorSearchDoesNotLeakPrivateDeletedOrPendingImages(){PictureSearchRequest request=new PictureSearchRequest();request.setPicColor("#FF0000");List<com.moxiang.picture.model.vo.PictureSearchVO.ColorMatch> result=discovery.byColor(request,actor);assertEquals(2,result.size());assertEquals(1L,result.get(0).getId());assertEquals(2L,result.get(1).getId());}
    @Test void scopedColorSearchOnlyReturnsThatSpace(){PictureSearchRequest request=new PictureSearchRequest();request.setPicColor("0xFF0000");request.setSpaceId(10L);assertEquals(3L,discovery.byColor(request,actor).get(0).getId());}
    PictureBatchEditRequest batchRequest(){PictureBatchEditRequest request=new PictureBatchEditRequest();request.setPictureIdList(Arrays.asList(1L,2L));request.setCategory("edited");request.setExpectedRevisions(new HashMap<>());request.getExpectedRevisions().put(1L,0L);request.getExpectedRevisions().put(2L,0L);return request;}
    @Test void batchCommitsBothPicturesAndRevisions(){assertEquals(2,batch.edit(batchRequest(),actor).getUpdatedCount());assertEquals("edited",pictures.selectById(1L).getCategory());assertEquals(1L,pictures.selectById(2L).getRevision());}
    @Test void mixedSpaceBatchDoesNotChangePublicPicture(){PictureBatchEditRequest request=batchRequest();request.setPictureIdList(Arrays.asList(1L,3L));request.getExpectedRevisions().remove(2L);request.getExpectedRevisions().put(3L,0L);assertThrows(BusinessException.class,()->batch.edit(request,actor));assertNull(pictures.selectById(1L).getCategory());assertEquals(0L,pictures.selectById(1L).getRevision());}
    @Test void secondSqlFailureRollsBackFirstActualDatabaseWrite()throws Exception{
        try(Connection c=dataSource.getConnection();Statement sql=c.createStatement()){sql.execute("ALTER TABLE picture ADD CONSTRAINT reject_second_name CHECK(name <> 'blocked2')");}
        PictureBatchEditRequest request=batchRequest();request.setNameRule("blocked{序号}");assertThrows(RuntimeException.class,()->batch.edit(request,actor));
        assertEquals("red",pictures.selectById(1L).getName());assertNull(pictures.selectById(1L).getCategory());assertEquals(0L,pictures.selectById(1L).getRevision());assertEquals("green",pictures.selectById(2L).getName());
    }
}
