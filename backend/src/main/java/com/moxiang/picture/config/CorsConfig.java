package com.moxiang.picture.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @org.springframework.beans.factory.annotation.Value("${picture.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toArray(String[]::new);
        if (origins.length == 0 || java.util.Arrays.asList(origins).contains("*"))
            throw new IllegalArgumentException("Credentialed CORS requires explicit frontend origins");
        // 覆盖所有请求
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 使用明确 Origin，不对任意外部站点开放携带 Cookie 的请求。
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Picture-Revision", "Content-Disposition");
    }
}
