package com.wastech.url_shortener.service;

import com.wastech.url_shortener.model.KeyRequest;
import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.repository.ShortenedUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // For read-only operations

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShorteningService {

    private final ShortenedUrlRepository shortenedUrlRepository;
    private final KeyGenerationService keyGenerationService;
    private final KafkaTemplate<String, KeyRequest> kafkaTemplate;
    private static final String URL_PERSISTENCE_TOPIC = "url-persistence-topic";

    @Transactional(readOnly = true)
    public Optional<String> getLongUrl(String shortCode) {
        return shortenedUrlRepository.findByShortCode(shortCode)
            .map(ShortenedUrl::getLongUrl);
    }

    public String shortenUrl(String longUrl) {
        log.info("Long Url", longUrl);
        // 1. Check if the long URL has already been shortened (to avoid duplicates for the same long URL)
        Optional<ShortenedUrl> existing = shortenedUrlRepository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            log.info("Long URL '{}' already shortened to '{}'", longUrl, existing.get().getShortCode());
            return existing.get().getShortCode();
        }

        // 2. Get a unique short code from the KGS
        String shortCode = keyGenerationService.getUniqueKey();
        if (shortCode == null) {
            log.error("Failed to get a unique key from KGS. Shortening failed for: {}", longUrl);
            throw new RuntimeException("Service unavailable: No unique keys available.");
        }

        // 3. Publish the mapping to Kafka for asynchronous persistence
        KeyRequest keyRequest = new KeyRequest(shortCode, longUrl);
        kafkaTemplate.send(URL_PERSISTENCE_TOPIC, shortCode, keyRequest)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully published mapping to Kafka: ShortCode={}, LongUrl={}", shortCode, longUrl);
                } else {
                    log.error("Failed to publish mapping to Kafka: ShortCode={}, LongUrl={}, Error={}",
                        shortCode, longUrl, ex.getMessage(), ex);
                    // TODO: Implement a retry mechanism or dead-letter queue for failed Kafka sends
                }
            });

        return shortCode;
    }
}