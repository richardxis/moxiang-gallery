package com.moxiang.picture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("picture.ai")
public class OutPaintingProperties {
    private boolean enabled = false;
    @lombok.ToString.Exclude
    private String apiKey = "";
    private String baseUrl = "https://dashscope.aliyuncs.com";
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 10000;
}
