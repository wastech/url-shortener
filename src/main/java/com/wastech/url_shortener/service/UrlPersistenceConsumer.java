package com.wastech.url_shortener.service;

import com.wastech.url_shortener.model.KeyRequest;
import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.repository.ShortenedUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlPersistenceConsumer {

    private final ShortenedUrlRepository shortenedUrlRepository;

    @KafkaListener(topics = "url-persistence-topic", groupId = "url-shortener-group", containerFactory = "kafkaListenerContainerFactory")
    @Transactional // Ensure the database write is atomic
    public void consumeUrlMapping(KeyRequest keyRequest) {
        log.info("Received message from Kafka: ShortCode={}, LongUrl={}", keyRequest.getShortCode(), keyRequest.getLongUrl());
        try {
            ShortenedUrl shortenedUrl = new ShortenedUrl(keyRequest.getShortCode(), keyRequest.getLongUrl());
            shortenedUrlRepository.save(shortenedUrl);
            log.info("Successfully persisted mapping: ShortCode={}, LongUrl={}", keyRequest.getShortCode(), keyRequest.getLongUrl());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // This typically happens if the unique constraint on short_code is violated.
            // In our KGS design, this should ideally not happen.
            // But if it does (e.g., KGS bug, Redis issue), we log it.
            log.error("Data integrity violation for ShortCode {}. It might already exist.", keyRequest.getShortCode(), e);
            // Optionally, handle this by marking the shortCode as invalid/releasing it back to the pool
            // though that adds complexity. Better to ensure KGS guarantees uniqueness.
        } catch (Exception e) {
            log.error("Failed to persist URL mapping for ShortCode={}, LongUrl={}: {}",
                keyRequest.getShortCode(), keyRequest.getLongUrl(), e.getMessage(), e);
            // TODO: Implement a dead-letter queue or retry mechanism for failed database writes
        }
    }
}