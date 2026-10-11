package com.moxiang.picture.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moxiang.picture.config.PictureCacheProperties;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import com.moxiang.picture.model.vo.PictureVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

@Slf4j
@Component
public class PublicPictureCache {
    @Data
    public static class Entry {
        private String payload;
        private long expiresAtMillis;
        public Entry() { }
        Entry(String payload, long expiresAtMillis) { this.payload = payload; this.expiresAtMillis = expiresAtMillis; }
    }
    private final PictureCacheProperties properties;
    private final PictureCacheStore store;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Cache<String, Entry> local;
    private final Object[] locks = new Object[64];
    private final LongAdder requests = new LongAdder(), l1Hits = new LongAdder(), l2Reads = new LongAdder(),
            l2Hits = new LongAdder(), databaseLoads = new LongAdder(), cacheErrors = new LongAdder(),
            invalidations = new LongAdder(), invalidationErrors = new LongAdder();
    private final LongAdder versionChecks = new LongAdder();
    @Autowired
    public PublicPictureCache(PictureCacheProperties properties, PictureCacheStore store, ObjectMapper mapper) {
        this(properties, store, mapper, Clock.systemUTC());
    }
    PublicPictureCache(PictureCacheProperties properties, PictureCacheStore store, ObjectMapper mapper, Clock clock) {
        if (properties.getMode() == null || properties.getNamespace() == null
                || !properties.getNamespace().matches("[A-Za-z0-9:_-]{1,80}")
                || properties.getMaximumSize() <= 0 || properties.getLocalTtlSeconds() <= 0
                || properties.getRedisTtlSeconds() <= 0 || properties.getJitterSeconds() < 0
                || (long)properties.getRedisTtlSeconds() + properties.getJitterSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("缓存配置无效");
        }
        this.properties = properties; this.store = store; this.mapper = mapper; this.clock = clock;
        local = Caffeine.newBuilder().maximumSize(properties.getMaximumSize())
                .expireAfterWrite(properties.getLocalTtlSeconds(), TimeUnit.SECONDS).build();
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }
    public Page<PictureVO> query(PictureQueryRequest input, Function<PictureQueryRequest, Page<PictureVO>> loader) {
        PictureQueryRequest query = PublicPictureQuery.normalize(input);
        requests.increment();
        if (properties.getMode() == PictureCacheProperties.Mode.NONE || !properties.isRedisEnabled()) return load(query, loader);
        String version, key;
        try {
            // 每次校验共享版本，跨实例写入后旧 L1 页不再可达。它有一次 Redis 元数据访问成本。
            version = currentVersion();
            key = PublicPictureQuery.key(query, version, mapper, properties.getNamespace());
        } catch (Exception e) { cacheError(e); return load(query, loader); }
        synchronized (locks[(key.hashCode() & Integer.MAX_VALUE) % locks.length]) {
            if (properties.getMode() == PictureCacheProperties.Mode.MULTILEVEL) {
                Entry entry = local.getIfPresent(key);
                Page<PictureVO> page = decode(entry);
                if (page != null) { l1Hits.increment(); return page; }
                if (entry != null) local.invalidate(key);
            }
            try {
                l2Reads.increment();
                String value = store.get(key);
                Entry entry = value == null ? null : mapper.readValue(value, Entry.class);
                Page<PictureVO> page = decode(entry);
                if (page != null) {
                    l2Hits.increment();
                    if (properties.getMode() == PictureCacheProperties.Mode.MULTILEVEL) local.put(key, entry);
                    return page;
                }
            } catch (Exception e) { cacheError(e); return load(query, loader); }
            // 数据库异常直接传播；不缓存失败，也不因缓存写入失败重跑业务查询。
            Page<PictureVO> page = load(query, loader);
            try {
                if (!version.equals(currentVersion())) return page;
                int ttl = properties.getRedisTtlSeconds() + (properties.getJitterSeconds() == 0 ? 0
                        : ThreadLocalRandom.current().nextInt(properties.getJitterSeconds()));
                Entry entry = new Entry(mapper.writeValueAsString(page), clock.millis() + ttl * 1000L);
                store.set(key, mapper.writeValueAsString(entry), ttl);
                if (properties.getMode() == PictureCacheProperties.Mode.MULTILEVEL) local.put(key, entry);
            } catch (Exception e) { cacheError(e); }
            return page;
        }
    }
    private Page<PictureVO> load(PictureQueryRequest query, Function<PictureQueryRequest, Page<PictureVO>> loader) {
        databaseLoads.increment();
        return loader.apply(query);
    }
    private Page<PictureVO> decode(Entry entry) {
        if (entry == null || entry.expiresAtMillis <= clock.millis()) return null;
        try { return mapper.readValue(entry.payload, new TypeReference<Page<PictureVO>>() { }); }
        catch (Exception e) { cacheError(e); return null; }
    }
    private void cacheError(Exception e) {
        cacheErrors.increment();
        log.warn("公共分页缓存不可用，走数据库；异常类型={}", e.getClass().getSimpleName());
    }
    private String currentVersion() { versionChecks.increment(); return store.currentVersion(); }
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidateNow(); }
            });
        } else invalidateNow();
    }
    private void invalidateNow() {
        local.invalidateAll();
        invalidations.increment();
        if (!properties.isRedisEnabled()) return;
        try { store.rotateVersion(); }
        catch (Exception e) {
            invalidationErrors.increment();
            log.error("数据库已提交，但公共分页缓存版本更新失败；需要关注失效告警", e);
        }
    }
    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", "current-process-since-start");
        result.put("mode", properties.getMode()); result.put("redisEnabled", properties.isRedisEnabled());
        result.put("requests", requests.sum()); result.put("l1Hits", l1Hits.sum());
        result.put("versionChecks", versionChecks.sum());
        result.put("l2Reads", l2Reads.sum()); result.put("l2Hits", l2Hits.sum());
        result.put("databaseLoads", databaseLoads.sum()); result.put("cacheErrors", cacheErrors.sum());
        result.put("invalidations", invalidations.sum()); result.put("invalidationErrors", invalidationErrors.sum());
        result.put("estimatedL1Entries", local.estimatedSize());
        result.put("l1HitRate", requests.sum() == 0 ? 0 : (double)l1Hits.sum() / requests.sum());
        result.put("l2ConditionalHitRate", l2Reads.sum() == 0 ? 0 : (double)l2Hits.sum() / l2Reads.sum());
        return result;
    }
}
