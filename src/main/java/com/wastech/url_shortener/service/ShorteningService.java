package com.wastech.url_shortener.service;

import com.wastech.url_shortener.dto.ShortenUrlResponse;
import com.wastech.url_shortener.model.KeyRequest;
import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.model.User;
import com.wastech.url_shortener.repository.ShortenedUrlRepository;
import com.wastech.url_shortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShorteningService {

    private final ShortenedUrlRepository shortenedUrlRepository;
    private final KeyGenerationService keyGenerationService;
    private final KafkaTemplate<String, KeyRequest> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    private static final String URL_PERSISTENCE_TOPIC = "url-persistence-topic";
    private static final String SHORT_CODE_CACHE_PREFIX = "shortCode:";
    private static final long CACHE_TTL_SECONDS = 3600;
    private static final long UNPAID_USER_EXPIRATION_DAYS = 7;

    @Transactional
    public Optional<ShortenUrlResponse> getLongUrl(String shortCode) {
        // 1. Try to get from Redis cache (still primarily for simple longUrl retrieval/redirection)
        String cachedLongUrl = stringRedisTemplate.opsForValue().get(SHORT_CODE_CACHE_PREFIX + shortCode);
        if (cachedLongUrl != null) {
            log.info("Found long URL for short code '{}' in Redis cache. Proceeding to DB for full details.", shortCode);
        }

        // 2. Get from database (always for full info, including click count, expiresAt)
        Optional<ShortenedUrl> shortenedUrlOptional = shortenedUrlRepository.findByShortCode(shortCode);

        if (shortenedUrlOptional.isPresent()) {
            ShortenedUrl shortenedUrl = shortenedUrlOptional.get();

            // Check for expiration
            if (shortenedUrl.getExpiresAt() != null && shortenedUrl.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("Short URL '{}' has expired for long URL '{}'.", shortCode, shortenedUrl.getLongUrl());
                return Optional.empty();
            }

            // Increment click count
            shortenedUrl.setClickCount(shortenedUrl.getClickCount() + 1);
            shortenedUrlRepository.save(shortenedUrl);

            // Update Redis cache with the latest longUrl
            stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + shortCode, shortenedUrl.getLongUrl(), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("Cached (or updated) long URL for short code '{}' in Redis.", shortCode);

            // Construct and return the full ShortenUrlResponse DTO
            ShortenUrlResponse response = new ShortenUrlResponse(
                shortenedUrl.getShortCode(),
                shortenedUrl.getLongUrl(),
                shortenedUrl.getClickCount(),
                shortenedUrl.getExpiresAt(),
                "URL details retrieved successfully."
            );
            return Optional.of(response);
        }

        return Optional.empty();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ShortenUrlResponse shortenUrl(String longUrl) {
        User currentUser = getCurrentAuthenticatedUser();


        if (currentUser == null) {
            throw new IllegalStateException("No authenticated user found.");
        }

        log.info("Attempting to shorten Long Url: {} for user: {}", longUrl, currentUser.getUsername());

        // 1. Check if the long URL has already been shortened by THIS USER
        Optional<ShortenedUrl> existing = shortenedUrlRepository.findByLongUrl(longUrl)
            .filter(s -> s.getUser() != null && s.getUser().getId().equals(currentUser.getId()));


        if (existing.isPresent()) {
            ShortenedUrl existingUrl = existing.get();
            String existingShortCode = existingUrl.getShortCode();
            Long existingClickCount = existingUrl.getClickCount();
            log.info("Long URL '{}' already shortened by user '{}' to '{}'. Returning existing short code and click count.", longUrl, currentUser.getUsername(), existingShortCode);
            stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + existingShortCode, longUrl, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return new ShortenUrlResponse(existingShortCode, existingUrl.getLongUrl(), existingClickCount, existingUrl.getExpiresAt(), "URL already shortened by you.");
        }

        // 2. Get a unique short code from the KGS
        String shortCode = keyGenerationService.getUniqueKey();
        if (shortCode == null) {
            log.error("Failed to get a unique key from KGS. Shortening failed for: {}", longUrl);
            throw new RuntimeException("Service unavailable: No unique keys available.");
        }

        LocalDateTime expiresAt = null;
        // Set expiration based on user's paid status
        if (!currentUser.isPaid()) {
            expiresAt = LocalDateTime.now().plusDays(UNPAID_USER_EXPIRATION_DAYS);
            log.info("Unpaid user: URL will expire in {} days.", UNPAID_USER_EXPIRATION_DAYS);
        } else {
            log.info("Paid user: URL will not expire.");
        }

        // 3. Publish the mapping to Kafka for asynchronous persistence with retry logic
        publishMappingToKafkaWithRetry(shortCode, longUrl, currentUser.getId(), expiresAt);

        // 4. Optionally, add to Redis cache immediately for new shortenings
        stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + shortCode, longUrl, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("New mapping for short code '{}' and long URL '{}' cached in Redis.", shortCode, longUrl);

        return new ShortenUrlResponse(shortCode, longUrl, 0L, expiresAt, "URL shortened successfully.");
    }

    @Retryable(
        value = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void publishMappingToKafkaWithRetry(String shortCode, String longUrl, Long userId, LocalDateTime expiresAt) {
        log.info("Attempting to publish mapping to Kafka (retry attempt): ShortCode={}, LongUrl={}, UserId={}", shortCode, longUrl, userId);
        KeyRequest keyRequest = new KeyRequest(shortCode, longUrl, userId, expiresAt);
        kafkaTemplate.send(URL_PERSISTENCE_TOPIC, shortCode, keyRequest)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish mapping to Kafka: ShortCode={}, LongUrl={}, UserId={}, Error={}",
                        shortCode, longUrl, userId, ex.getMessage(), ex);
                    throw new RuntimeException("Kafka send failed", ex);
                } else {
                    log.info("Successfully published mapping to Kafka: ShortCode={}, LongUrl={}, UserId={}", shortCode, longUrl, userId);
                }
            })
            .join();
    }

    @Recover
    public void recover(Exception e, String shortCode, String longUrl, Long userId, LocalDateTime expiresAt) {
        log.error("All Kafka send retries failed for ShortCode={}, LongUrl={}, UserId={}. Error: {}", shortCode, longUrl, userId, e.getMessage());
        log.warn("Shortening operation completed for {} with short code {}, but Kafka persistence failed after retries for user {}. " +
                "The short code might be in Redis but not in DB. Manual intervention or a recovery process is needed.",
            longUrl, shortCode, userId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ShortenUrlResponse> getUserShortenedUrls() {
        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated user found.");
        }
        return shortenedUrlRepository.findByUser(currentUser).stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<ShortenUrlResponse> getAllShortenedUrls() {
        return shortenedUrlRepository.findAll().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<ShortenUrlResponse> getShortenedUrlsBySpecificUserId(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User with ID " + userId + " not found."));
        return shortenedUrlRepository.findByUser(user).stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ShortenedUrl updateShortenedUrl(Long id, String newLongUrl) {
        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated user found.");
        }

        ShortenedUrl urlToUpdate = shortenedUrlRepository.findByIdAndUser(id, currentUser)
            .orElseThrow(() -> new IllegalArgumentException("Shortened URL not found or not owned by user."));

        // Invalidate cache for the old short code
        stringRedisTemplate.delete(SHORT_CODE_CACHE_PREFIX + urlToUpdate.getShortCode());
        log.info("Invalidated cache for short code: {}", urlToUpdate.getShortCode());

        urlToUpdate.setLongUrl(newLongUrl);
        ShortenedUrl updatedUrl = shortenedUrlRepository.save(urlToUpdate);

        // Update cache with new long URL
        stringRedisTemplate.opsForValue().set(SHORT_CODE_CACHE_PREFIX + updatedUrl.getShortCode(), updatedUrl.getLongUrl(), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("Updated cache for short code: {} with new long URL.", updatedUrl.getShortCode());

        return updatedUrl;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deleteShortenedUrl(Long id) {
        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated user found.");
        }

        ShortenedUrl urlToDelete = shortenedUrlRepository.findByIdAndUser(id, currentUser)
            .orElseThrow(() -> new IllegalArgumentException("Shortened URL not found or not owned by user."));

        stringRedisTemplate.delete(SHORT_CODE_CACHE_PREFIX + urlToDelete.getShortCode());
        log.info("Invalidated cache for short code: {}", urlToDelete.getShortCode());

        shortenedUrlRepository.delete(urlToDelete);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAllShortenedUrls() {
        log.warn("ADMIN: Deleting ALL shortened URLs from the database.");
        List<String> shortCodes = shortenedUrlRepository.findAll().stream()
            .map(ShortenedUrl::getShortCode)
            .map(s -> SHORT_CODE_CACHE_PREFIX + s)
            .collect(Collectors.toList());
        if (!shortCodes.isEmpty()) {
            stringRedisTemplate.delete(shortCodes);
            log.info("Cleared {} short URL entries from Redis cache during bulk delete.", shortCodes.size());
        }
        shortenedUrlRepository.deleteAll();
        log.info("Successfully deleted all shortened URLs from the database.");
    }

    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() instanceof String) {
            return null;
        }
        User userDetails = (User) authentication.getPrincipal();

        return userRepository.findByUsername(userDetails.getUsername()).orElse(null);
    }

    private ShortenUrlResponse convertToDto(ShortenedUrl shortenedUrl) {
        return new ShortenUrlResponse(
            shortenedUrl.getShortCode(),
            shortenedUrl.getLongUrl(),
            shortenedUrl.getClickCount(),
            shortenedUrl.getExpiresAt(),
            "Retrieved successfully."
        );
    }
}