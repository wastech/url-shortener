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
// @Validated // No longer strictly needed here if @Valid is used on DTO, but can keep if other method validations exist
public class UrlController {

    private final ShorteningService shorteningService;

    // Endpoint for shortening a URL
    @PostMapping("/shorten")
    public ResponseEntity<String> shortenUrl(@Valid @RequestBody ShortenUrlRequest request) { // Use @Valid on DTO
        String longUrl = request.getLongUrl();
        log.info("Received request to shorten URL: {}", longUrl);
        try {
            String shortCode = shorteningService.shortenUrl(longUrl);
            // In a real app, you'd return the full shortened URL like "https://yourdomain.com/shortCode"
            // For now, returning just the shortCode as per your current code.
            return ResponseEntity.ok(shortCode);
        } catch (Exception e) {
            log.error("Error shortening URL {}: {}", longUrl, e.getMessage(), e);
            // For production, you might want more specific error messages or custom error DTOs
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing your request: " + e.getMessage());
        }
    }

    // Endpoint for redirecting from short URL to long URL
    @GetMapping("/{shortCode}")
    public RedirectView redirectToLongUrl(@PathVariable String shortCode) { // Removed HttpServletResponse
        log.info("Received request for short code: {}", shortCode);
        String longUrl = shorteningService.getLongUrl(shortCode)
            .orElse(null);

        if (longUrl != null) {
            RedirectView redirectView = new RedirectView();
            redirectView.setUrl(longUrl);
            redirectView.setStatusCode(HttpStatus.MOVED_PERMANENTLY); // 301 Moved Permanently
            log.info("Redirecting short code {} to long URL {}", shortCode, longUrl);
            return redirectView;
        } else {
            log.warn("Short code {} not found.", shortCode);
            // Throwing ResponseStatusException results in a proper HTTP 404 response
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found.");
        }
    }
}