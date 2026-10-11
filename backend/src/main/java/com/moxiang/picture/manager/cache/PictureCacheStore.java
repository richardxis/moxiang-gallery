package com.moxiang.picture.manager.cache;

/** 实际实现使用 Redis，测试使用可控替身，不将替身称为 Redis 集成测试。 */
public interface PictureCacheStore {
    String currentVersion();
    String get(String key);
    void set(String key, String value, int ttlSeconds);
    void rotateVersion();
}
