package com.wastech.url_shortener.controller;

import com.wastech.url_shortener.dto.ShortenUrlRequest;
import com.wastech.url_shortener.service.ShorteningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final ShorteningService shorteningService;

    @PostMapping("/shorten")
    public ResponseEntity<String> shortenUrl(@Valid @RequestBody ShortenUrlRequest request) {
        String longUrl = request.getLongUrl();

        // Enhanced debugging
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
            String shortCode = shorteningService.shortenUrl(longUrl);
            return ResponseEntity.ok(shortCode);
        } catch (Exception e) {
            log.error("Error shortening URL {}: {}", longUrl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing your request: " + e.getMessage());
        }
    }

    @GetMapping("/{shortCode}")
    public RedirectView redirectToLongUrl(@PathVariable String shortCode) {
        log.info("Received request for short code: {}", shortCode);
        String longUrl = shorteningService.getLongUrl(shortCode)
            .orElse(null);

        if (longUrl != null) {
            RedirectView redirectView = new RedirectView();
            redirectView.setUrl(longUrl);
            redirectView.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            log.info("Redirecting short code {} to long URL {}", shortCode, longUrl);
            return redirectView;
        } else {
            log.warn("Short code {} not found.", shortCode);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found.");
        }
    }
}