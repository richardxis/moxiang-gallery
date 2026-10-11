package com.moxiang.picture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "picture.cache")
public class PictureCacheProperties {
    public enum Mode { NONE, REDIS, MULTILEVEL }
    private Mode mode = Mode.MULTILEVEL;
    private String namespace = "moxiang:picture";
    // 没有显式启用时不尝试外部连接；单独启用 Redis 不等于已实测可用。
    private boolean redisEnabled = false;
    private long maximumSize = 10000;
    private long localTtlSeconds = 300;
    private int redisTtlSeconds = 300;
    private int jitterSeconds = 300;
}
