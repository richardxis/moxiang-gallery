package com.moxiang.picture.manager.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import com.moxiang.picture.config.PictureCacheProperties;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RedisPictureCacheStore implements PictureCacheStore {
    private final String versionKey;
    private final StringRedisTemplate redis;
    public RedisPictureCacheStore(StringRedisTemplate redis, PictureCacheProperties properties) {
        this.redis = redis;
        this.versionKey = properties.getNamespace() + ":public:version";
    }
    @Override public String currentVersion() {
        String version = redis.opsForValue().get(versionKey);
        if (version == null) {
            redis.opsForValue().setIfAbsent(versionKey, UUID.randomUUID().toString());
            version = redis.opsForValue().get(versionKey);
        }
        if (version == null || version.isEmpty()) throw new IllegalStateException("缓存版本不可用");
        return version;
    }
    @Override public String get(String key) { return redis.opsForValue().get(key); }
    @Override public void set(String key, String value, int ttlSeconds) {
        redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }
    @Override public void rotateVersion() {
        // 不使用固定初始值，避免 Redis 重建版本时重新命中历史键。
        redis.opsForValue().set(versionKey, UUID.randomUUID().toString());
    }
}
