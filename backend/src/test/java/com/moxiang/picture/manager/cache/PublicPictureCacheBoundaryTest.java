package com.moxiang.picture.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moxiang.picture.config.PictureCacheProperties;
import com.moxiang.picture.exception.BusinessException;
import com.moxiang.picture.model.dto.picture.PictureQueryRequest;
import com.moxiang.picture.model.vo.PictureVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PublicPictureCacheBoundaryTest {
    static class Store implements PictureCacheStore {
        final Map<String, String> pages = new HashMap<>();
        String version = "initial";
        boolean failVersion, failGet, failSet, failRotate;
        int lastTtl, rotations;
        public String currentVersion() { if (failVersion) throw new IllegalStateException("unavailable"); return version; }
        public String get(String key) { if (failGet) throw new IllegalStateException("unavailable"); return pages.get(key); }
        public void set(String key, String value, int ttl) {
            if (failSet) throw new IllegalStateException("unavailable"); pages.put(key, value); lastTtl = ttl;
        }
        public void rotateVersion() {
            if (failRotate) throw new IllegalStateException("unavailable");
            version = UUID.randomUUID().toString(); rotations++;
        }
    }
    static class MutableClock extends Clock {
        long millis = 100000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis); }
        public long millis() { return millis; }
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private PictureCacheProperties enabled() {
        PictureCacheProperties properties = new PictureCacheProperties();
        properties.setRedisEnabled(true); return properties;
    }
    private PublicPictureCache cache(Store store) { return new PublicPictureCache(enabled(), store, mapper, new MutableClock()); }
    private Page<PictureVO> page(long id) {
        PictureVO picture = new PictureVO(); picture.setId(id); picture.setTags(Arrays.asList("资料"));
        Page<PictureVO> page = new Page<>(1, 10, 1); page.setRecords(Collections.singletonList(picture)); return page;
    }
    private long count(PublicPictureCache cache, String field) { return ((Number)cache.statistics().get(field)).longValue(); }

    @Test void separatesPagesAndSearchConditions() {
        PictureQueryRequest a = new PictureQueryRequest(), b = new PictureQueryRequest();
        String first = PublicPictureQuery.key(PublicPictureQuery.normalize(a), "v", mapper);
        b.setCurrent(2);
        assertNotEquals(first, PublicPictureQuery.key(PublicPictureQuery.normalize(b), "v", mapper));
        b.setCurrent(1); b.setSearchText("cat");
        assertNotEquals(first, PublicPictureQuery.key(PublicPictureQuery.normalize(b), "v", mapper));
        b.setSearchText(null); b.setPageSize(12);
        assertNotEquals(first, PublicPictureQuery.key(PublicPictureQuery.normalize(b), "v", mapper));
    }
    @Test void normalizesEquivalentTagsAndSortDefaults() {
        PictureQueryRequest a = new PictureQueryRequest(), b = new PictureQueryRequest();
        a.setTags(Arrays.asList("校园", "资料", "校园")); b.setTags(Arrays.asList(" 资料 ", "校园"));
        b.setSortField("createTime"); b.setSortOrder("descend");
        assertEquals(PublicPictureQuery.key(PublicPictureQuery.normalize(a), "v", mapper),
                PublicPictureQuery.key(PublicPictureQuery.normalize(b), "v", mapper));
    }
    @Test void isolatesCacheKeysByEnvironmentNamespace() {
        PictureQueryRequest query = PublicPictureQuery.normalize(new PictureQueryRequest());
        assertNotEquals(PublicPictureQuery.key(query, "v", mapper, "moxiang:picture:test"),
                PublicPictureQuery.key(query, "v", mapper, "moxiang:picture:prod"));
    }
    @Test void enforcesApprovedPublicScopeAndRejectsSpaceQueries() {
        PictureQueryRequest input = new PictureQueryRequest(); input.setReviewStatus(0); input.setNullSpaceId(false);
        PictureQueryRequest normalized = PublicPictureQuery.normalize(input);
        assertEquals(1, normalized.getReviewStatus()); assertTrue(normalized.isNullSpaceId());
        assertEquals(0, input.getReviewStatus()); // 不修改调用者 DTO。
        input.setSpaceId(100L);
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalize(input));
    }
    @Test void localHitReturnsTypedIndependentPage() {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        PictureQueryRequest query = new PictureQueryRequest();
        cache.query(query, q -> page(9007199254740993L));
        Page<PictureVO> cached = cache.query(query, q -> { fail("unexpected database read"); return null; });
        assertEquals(Long.valueOf(9007199254740993L), cached.getRecords().get(0).getId());
        cached.getRecords().get(0).setName("changed by caller");
        assertNull(cache.query(query, q -> page(2)).getRecords().get(0).getName());
        assertEquals(2, count(cache, "l1Hits")); assertEquals(1, count(cache, "databaseLoads"));
    }
    @Test void anotherInstanceReadsSharedCacheThenFillsLocal() {
        Store store = new Store(); PublicPictureCache a = cache(store), b = cache(store);
        a.query(new PictureQueryRequest(), q -> page(1));
        assertEquals(Long.valueOf(1), b.query(new PictureQueryRequest(), q -> page(2)).getRecords().get(0).getId());
        assertEquals(1, count(b, "l2Hits")); assertEquals(0, count(b, "databaseLoads"));
        b.query(new PictureQueryRequest(), q -> page(3)); assertEquals(1, count(b, "l1Hits"));
    }
    @Test void versionRotationInvalidatesOtherInstancesLocalPages() {
        Store store = new Store(); PublicPictureCache a = cache(store), b = cache(store);
        a.query(new PictureQueryRequest(), q -> page(1)); b.query(new PictureQueryRequest(), q -> page(1));
        a.invalidateAfterCommit();
        assertEquals(Long.valueOf(2), b.query(new PictureQueryRequest(), q -> page(2)).getRecords().get(0).getId());
        assertEquals(1, count(b, "databaseLoads"));
    }
    @Test void versionChangeDuringDatabaseReadPreventsStaleFill() {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        cache.query(new PictureQueryRequest(), q -> { store.rotateVersion(); return page(1); });
        assertTrue(store.pages.isEmpty());
        cache.query(new PictureQueryRequest(), q -> page(2)); assertEquals(2, count(cache, "databaseLoads"));
    }
    @Test void redisVersionOutageBypassesEvenExistingLocalCache() {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        cache.query(new PictureQueryRequest(), q -> page(1)); store.failVersion = true;
        assertEquals(Long.valueOf(2), cache.query(new PictureQueryRequest(), q -> page(2)).getRecords().get(0).getId());
        assertEquals(1, count(cache, "cacheErrors"));
    }
    @Test void redisPageReadFailureFallsBackToDatabase() {
        Store store = new Store(); store.failGet = true;
        PublicPictureCache cache = cache(store);
        assertEquals(Long.valueOf(1), cache.query(new PictureQueryRequest(), q -> page(1)).getRecords().get(0).getId());
        assertEquals(1, count(cache, "databaseLoads"));
    }
    @Test void redisWriteFailureDoesNotRepeatDatabaseQuery() {
        Store store = new Store(); store.failSet = true; PublicPictureCache cache = cache(store);
        AtomicInteger loads = new AtomicInteger();
        assertNotNull(cache.query(new PictureQueryRequest(), q -> { loads.incrementAndGet(); return page(1); }));
        assertEquals(1, loads.get()); assertTrue(store.pages.isEmpty());
    }
    @Test void corruptedSharedValueFallsBackToDatabase() {
        Store store = new Store(); PictureQueryRequest query = new PictureQueryRequest();
        store.pages.put(PublicPictureQuery.key(PublicPictureQuery.normalize(query), store.version, mapper), "not-json");
        PublicPictureCache cache = cache(store); assertNotNull(cache.query(query, q -> page(1)));
        assertEquals(1, count(cache, "cacheErrors"));
    }
    @Test void databaseFailureIsNotCached() {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        assertThrows(IllegalStateException.class, () -> cache.query(new PictureQueryRequest(), q -> { throw new IllegalStateException("database"); }));
        assertTrue(store.pages.isEmpty());
        assertNotNull(cache.query(new PictureQueryRequest(), q -> page(1)));
        assertEquals(2, count(cache, "databaseLoads"));
    }
    @Test void noneModeDoesNotAccessSharedCache() {
        Store store = new Store(); store.failVersion = true;
        PictureCacheProperties properties = enabled(); properties.setMode(PictureCacheProperties.Mode.NONE);
        PublicPictureCache cache = new PublicPictureCache(properties, store, mapper, new MutableClock());
        cache.query(new PictureQueryRequest(), q -> page(1)); cache.query(new PictureQueryRequest(), q -> page(1));
        assertEquals(2, count(cache, "databaseLoads")); assertEquals(0, count(cache, "cacheErrors"));
    }
    @Test void redisOnlyModeSkipsLocalCache() {
        Store store = new Store(); PictureCacheProperties properties = enabled(); properties.setMode(PictureCacheProperties.Mode.REDIS);
        PublicPictureCache cache = new PublicPictureCache(properties, store, mapper, new MutableClock());
        cache.query(new PictureQueryRequest(), q -> page(1)); cache.query(new PictureQueryRequest(), q -> page(1));
        assertEquals(0, count(cache, "l1Hits")); assertEquals(1, count(cache, "l2Hits"));
    }
    @Test void absoluteExpiryPreventsLocalCacheFromExtendingSharedDataLifetime() {
        Store store = new Store(); MutableClock clock = new MutableClock();
        PublicPictureCache cache = new PublicPictureCache(enabled(), store, mapper, clock);
        cache.query(new PictureQueryRequest(), q -> page(1));
        assertTrue(store.lastTtl >= 300 && store.lastTtl < 600);
        clock.millis += 600001;
        assertEquals(Long.valueOf(2), cache.query(new PictureQueryRequest(), q -> page(2)).getRecords().get(0).getId());
    }
    @Test void transactionInvalidationWaitsUntilCommit() {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.invalidateAfterCommit(); assertEquals(0, store.rotations);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCommit();
            assertEquals(1, store.rotations);
        } finally { TransactionSynchronizationManager.clear(); }
    }
    @Test void rollbackDoesNotRotateCacheVersion() {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.invalidateAfterCommit();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations())
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertEquals(0, store.rotations);
        } finally { TransactionSynchronizationManager.clear(); }
    }
    @Test void failedInvalidationIsRecordedWithoutFailingCommittedWrite() {
        Store store = new Store(); store.failRotate = true; PublicPictureCache cache = cache(store);
        assertDoesNotThrow(cache::invalidateAfterCommit); assertEquals(1, count(cache, "invalidationErrors"));
    }
    @Test void sameKeyConcurrentMissesShareOneLocalDatabaseLoad() throws Exception {
        Store store = new Store(); PublicPictureCache cache = cache(store);
        AtomicInteger loads = new AtomicInteger();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Page<PictureVO>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await(); return cache.query(new PictureQueryRequest(), q -> { loads.incrementAndGet(); return page(1); });
            }));
            start.countDown();
            for (java.util.concurrent.Future<Page<PictureVO>> future : futures) assertNotNull(future.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally { pool.shutdownNow(); }
    }
}
