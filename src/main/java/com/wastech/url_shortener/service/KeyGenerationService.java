package com.wastech.url_shortener.service;

import com.wastech.url_shortener.util.Base62;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyGenerationService {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private static final String KEY_POOL_SET = "shortener:key_pool";
    private static final String KEY_COUNTER = "shortener:key_counter";
    private static final String KEY_GEN_LOCK = "shortener:key_gen_lock";
    private static final int MIN_KEY_POOL_SIZE = 10000;
    private static final int GENERATE_BATCH_SIZE = 50000;
    private static final int SHORT_CODE_LENGTH = 7;

    // Scheduled to run periodically to check and replenish the key pool
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void replenishKeyPool() {
        RLock lock = redissonClient.getLock(KEY_GEN_LOCK);
        try {
            // Acquire lock with a timeout to prevent deadlocks
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                Long currentPoolSize = redisTemplate.opsForSet().size(KEY_POOL_SET);
                log.info("Current key pool size: {}", currentPoolSize);

                if (currentPoolSize == null || currentPoolSize < MIN_KEY_POOL_SIZE) {
                    log.info("Replenishing key pool. Generating {} new keys.", GENERATE_BATCH_SIZE);
                    generateAndAddKeys(GENERATE_BATCH_SIZE);
                }
            } else {
                log.warn("Could not acquire key generation lock. Another instance might be generating keys.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Key generation interrupted: {}", e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void generateAndAddKeys(int count) {
        Set<String> newKeys = new java.util.HashSet<>();
        long startId = redisTemplate.opsForValue().increment(KEY_COUNTER, 1) - 1;
        long nextIdToGenerate = startId;

        for (int i = 0; i < count; i++) {
            String newKey = Base62.encodeWithPadding(nextIdToGenerate++, SHORT_CODE_LENGTH);
            newKeys.add(newKey);
        }

        redisTemplate.opsForSet().add(KEY_POOL_SET, newKeys.toArray(new String[0]));
        log.info("Added {} new keys to the pool.", newKeys.size());
    }

    public String getUniqueKey() {
        String key = redisTemplate.opsForSet().pop(KEY_POOL_SET);
        if (key == null) {
            log.error("Key pool is empty! Attempting to generate on demand (emergency).");

            long emergencyId = redisTemplate.opsForValue().increment(KEY_COUNTER);
            key = Base62.encodeWithPadding(emergencyId, SHORT_CODE_LENGTH);
            log.warn("Emergency generated key: {}", key);

        }
        return key;
    }

    public Long getKeyPoolSize() {
        return redisTemplate.opsForSet().size(KEY_POOL_SET);
    }
}