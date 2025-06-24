package com.wastech.url_shortener.controller;

import com.wastech.url_shortener.dto.ShortenUrlRequest;
import com.wastech.url_shortener.dto.ShortenUrlResponse;
import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.service.ShorteningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/urls")
public class UrlController {

    private final ShorteningService shorteningService;

    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shortenUrl(@Valid @RequestBody ShortenUrlRequest request) {
        String longUrl = request.getLongUrl();

        log.info("=== URL Shortening Request Debug ===");
        log.info("Request object: {}", request);
        log.info("Long URL received: '{}'", longUrl);
        log.info("Long URL is null: {}", longUrl == null);
        log.info("Long URL is blank: {}", longUrl == null || longUrl.trim().isEmpty());
        if (longUrl != null) {
            log.info("Long URL length: {}", longUrl.length());
            log.info("Long URL starts with http: {}", longUrl.startsWith("http"));
            log.info("Long URL starts with https: {}", longUrl.startsWith("https"));
        }
        log.info("=== End Debug ===");

        try {
            ShortenUrlResponse response = shorteningService.shortenUrl(longUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error shortening URL {}: {}", longUrl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ShortenUrlResponse(null, null, null, null, "Error processing your request: " + e.getMessage()));
        }
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<ShortenUrlResponse> getUrlDetails(@PathVariable String shortCode) {
        log.info("Received request for short code details: {}", shortCode);
        Optional<ShortenUrlResponse> responseOptional = shorteningService.getLongUrl(shortCode);

        if (responseOptional.isPresent()) {
            ShortenUrlResponse response = responseOptional.get();
            log.info("Found URL details for short code {}: {}", shortCode, response.getLongUrl());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Short code {} not found or expired.", shortCode);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found or expired.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShortenUrlResponse> updateUrl(@PathVariable Long id, @RequestBody String newLongUrl) {
        ShortenedUrl updatedUrl = shorteningService.updateShortenedUrl(id, newLongUrl);

        ShortenUrlResponse response = new ShortenUrlResponse(
            updatedUrl.getShortCode(),
            updatedUrl.getLongUrl(),
            updatedUrl.getClickCount(),
            updatedUrl.getExpiresAt(),
            "URL updated successfully."
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUrl(@PathVariable Long id) {
        shorteningService.deleteShortenedUrl(id);
        return ResponseEntity.ok("URL deleted successfully.");
    }

    @GetMapping("/my-urls")
    public ResponseEntity<List<ShortenUrlResponse>> getMyUrls() {
        List<ShortenUrlResponse> urls = shorteningService.getUserShortenedUrls();
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/admin/all")
    public ResponseEntity<List<ShortenUrlResponse>> getAllUrlsForAdmin() {
        List<ShortenUrlResponse> urls = shorteningService.getAllShortenedUrls();
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/admin/user/{userId}")
    public ResponseEntity<List<ShortenUrlResponse>> getUrlsByUserIdForAdmin(@PathVariable Long userId) {
        List<ShortenUrlResponse> urls = shorteningService.getShortenedUrlsBySpecificUserId(userId);
        return ResponseEntity.ok(urls);
    }

    @DeleteMapping("/admin/all")
    public ResponseEntity<String> deleteAllUrlsForAdmin() {
        shorteningService.deleteAllShortenedUrls();
        return ResponseEntity.ok("All URLs deleted successfully by admin.");
    }
}