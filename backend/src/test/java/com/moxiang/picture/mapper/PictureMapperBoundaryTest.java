package com.moxiang.picture.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class PictureMapperBoundaryTest {
    @Test void mapperXmlTargetsBusinessEntityAndIncludesOptimizationFields() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        String resource = "generator/mapper/PictureMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        assertTrue(configuration.hasMapper(PictureMapper.class));
        assertEquals(com.moxiang.picture.model.entity.Picture.class,
                configuration.getResultMap("com.moxiang.picture.mapper.PictureMapper.BaseResultMap").getType());
        java.util.Set<String> properties = new java.util.HashSet<>();
        configuration.getResultMap("com.moxiang.picture.mapper.PictureMapper.BaseResultMap").getResultMappings()
                .forEach(mapping -> properties.add(mapping.getProperty()));
        assertTrue(properties.containsAll(java.util.Arrays.asList("thumbnailUrl", "picColor", "spaceId")));
    }
}
