package com.moxiang.picture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("picture.image-search")
public class ImageSearchProperties {
    private boolean enabled = false;
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 5000;
}
