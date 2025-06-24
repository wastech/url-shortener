package com.wastech.url_shortener.service;

import com.wastech.url_shortener.model.KeyRequest;
import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.model.User;
import com.wastech.url_shortener.repository.ShortenedUrlRepository;
import com.wastech.url_shortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlPersistenceConsumer {


    private final ShortenedUrlRepository shortenedUrlRepository;
    private final UserRepository userRepository;

    @KafkaListener(topics = "url-persistence-topic", groupId = "url-shortener-group")
    @Transactional
    public void processUrlPersistence(KeyRequest keyRequest) {
        log.info("Received message for URL persistence: {}", keyRequest);

        try {
            User user = userRepository.findById(keyRequest.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found for ID: " + keyRequest.getUserId()));


            Optional<ShortenedUrl> existingUrlOptional = shortenedUrlRepository.findByShortCode(keyRequest.getShortCode());

            if (existingUrlOptional.isEmpty()) {
                ShortenedUrl shortenedUrl = new ShortenedUrl();
                shortenedUrl.setShortCode(keyRequest.getShortCode());
                shortenedUrl.setLongUrl(keyRequest.getLongUrl());
                shortenedUrl.setCreatedAt(LocalDateTime.now());
                shortenedUrl.setClickCount(0L);
                shortenedUrl.setUser(user);
                shortenedUrl.setExpiresAt(keyRequest.getExpiresAt());

                shortenedUrlRepository.save(shortenedUrl);
                log.info("Successfully persisted new shortened URL: {}", shortenedUrl.getShortCode());
            } else {
                log.warn("Short code '{}' already exists in DB. Skipping persistence for this message.", keyRequest.getShortCode());

            }

        } catch (Exception e) {
            log.error("Error processing URL persistence message for shortCode {}: {}", keyRequest.getShortCode(), e.getMessage(), e);
        }
    }
}