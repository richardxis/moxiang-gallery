package com.moxiang.picture.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CorsConfigBoundaryTest {
    static class Registry extends CorsRegistry { Map<String, CorsConfiguration> configs() { return getCorsConfigurations(); } }
    @Test void permitsOnlyConfiguredOrigins() {
        CorsConfig config = new CorsConfig(); ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:5173, http://127.0.0.1:5173");
        Registry registry = new Registry(); config.addCorsMappings(registry); CorsConfiguration cors = registry.configs().get("/**");
        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173")); assertNull(cors.checkOrigin("https://attacker.example")); assertTrue(cors.getAllowCredentials());
    }
    @Test void refusesWildcardCredentialsConfiguration() {
        CorsConfig config = new CorsConfig(); ReflectionTestUtils.setField(config, "allowedOrigins", "*"); assertThrows(IllegalArgumentException.class, () -> config.addCorsMappings(new Registry()));
    }
}
