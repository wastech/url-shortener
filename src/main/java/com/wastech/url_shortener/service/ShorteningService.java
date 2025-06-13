package com.wastech.url_shortener.service;

import com.wastech.url_shortener.model.KeyRequest;
import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.repository.ShortenedUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShorteningService {

    private final ShortenedUrlRepository shortenedUrlRepository;
    private final KeyGenerationService keyGenerationService;
    private final KafkaTemplate<String, KeyRequest> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String URL_PERSISTENCE_TOPIC = "url-persistence-topic";
    private static final String SHORT_CODE_CACHE_PREFIX = "shortCode:";
    private static final long CACHE_TTL_SECONDS = 3600;

    @Transactional(readOnly = true)
    public Optional<String> getLongUrl(String shortCode) {
        // 1. Try to get from Redis cache
        String cachedLongUrl = stringRedisTemplate.opsForValue().get(SHORT_CODE_CACHE_PREFIX + shortCode);
        if (cachedLongUrl != null) {
            log.info("Found long URL for short code '{}' in Redis cache.", shortCode);
            return Optional.of(cachedLongUrl);
        }

        // 2. If not in cache, get from database
        Optional<String> longUrlFromDb = shortenedUrlRepository.findByShortCode(shortCode)
            .map(ShortenedUrl::getLongUrl);

        // 3. If found in database, cache it in Redis
        longUrlFromDb.ifPresent(longUrl -> {
            stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + shortCode, longUrl, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Cached long URL for short code '{}' in Redis.", shortCode);
        });

        return longUrlFromDb;
    }

    public String shortenUrl(String longUrl) {
        log.info("Attempting to shorten Long Url: {}", longUrl);

        // 1. Check if the long URL has already been shortened (to avoid duplicates for the same long URL)
        Optional<ShortenedUrl> existing = shortenedUrlRepository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            String existingShortCode = existing.get().getShortCode();
            log.info("Long URL '{}' already shortened to '{}'. Returning existing short code.", longUrl, existingShortCode);
            // Also update cache if it's somehow missing or expired
            stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + existingShortCode, longUrl, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return existingShortCode;
        }

        // 2. Get a unique short code from the KGS
        String shortCode = keyGenerationService.getUniqueKey();
        if (shortCode == null) {
            log.error("Failed to get a unique key from KGS. Shortening failed for: {}", longUrl);
            throw new RuntimeException("Service unavailable: No unique keys available.");
        }

        // 3. Publish the mapping to Kafka for asynchronous persistence with retry logic
        publishMappingToKafkaWithRetry(shortCode, longUrl);

        // 4. Optionally, add to Redis cache immediately for new shortenings
        stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + shortCode, longUrl, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("New mapping for short code '{}' and long URL '{}' cached in Redis.", shortCode, longUrl);


        return shortCode;
    }

    @Retryable(
        value = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void publishMappingToKafkaWithRetry(String shortCode, String longUrl) {
        log.info("Attempting to publish mapping to Kafka (retry attempt): ShortCode={}, LongUrl={}", shortCode, longUrl);
        KeyRequest keyRequest = new KeyRequest(shortCode, longUrl);
        kafkaTemplate.send(URL_PERSISTENCE_TOPIC, shortCode, keyRequest)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish mapping to Kafka: ShortCode={}, LongUrl={}, Error={}",
                        shortCode, longUrl, ex.getMessage(), ex);
                    throw new RuntimeException("Kafka send failed", ex); // Re-throw to trigger retry
                } else {
                    log.info("Successfully published mapping to Kafka: ShortCode={}, LongUrl={}", shortCode, longUrl);
                }
            })
            .join();
    }

    @Recover
    public void recover(Exception e, String shortCode, String longUrl) {
        log.error("All Kafka send retries failed for ShortCode={}, LongUrl={}. Error: {}", shortCode, longUrl, e.getMessage());
        // TODO: Implement a more robust fallback mechanism here
        log.warn("Shortening operation completed for {} with short code {}, but Kafka persistence failed after retries.", longUrl, shortCode);
    }
}