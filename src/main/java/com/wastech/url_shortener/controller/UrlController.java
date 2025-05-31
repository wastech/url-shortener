package com.wastech.url_shortener.controller;

import com.wastech.url_shortener.service.ShorteningService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Slf4j
@Validated // Enable validation on controller methods
public class UrlController {

    private final ShorteningService shorteningService;

    // Endpoint for shortening a URL
    @PostMapping("/shorten")
    public ResponseEntity<String> shortenUrl(
        @RequestBody @NotBlank(message = "URL cannot be empty")
        @Pattern(regexp = "^(http|https)://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/\\S*)?$",
            message = "Invalid URL format")
        String longUrl) {
        log.info("Received request to shorten URL: {}", longUrl);
        try {
            String shortCode = shorteningService.shortenUrl(longUrl);
            // In a real app, you'd return the full shortened URL like "https://yourdomain.com/shortCode"
            return ResponseEntity.ok(shortCode);
        } catch (Exception e) {
            log.error("Error shortening URL {}: {}", longUrl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing your request: " + e.getMessage());
        }
    }

    // Endpoint for redirecting from short URL to long URL
    @GetMapping("/{shortCode}")
    public RedirectView redirectToLongUrl(@PathVariable String shortCode, HttpServletResponse response) throws IOException {
        log.info("Received request for short code: {}", shortCode);
        String longUrl = shorteningService.getLongUrl(shortCode)
            .orElse(null);

        if (longUrl != null) {
            // Use 301 Moved Permanently for SEO benefits and caching
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader(HttpHeaders.LOCATION, longUrl);
            log.info("Redirecting short code {} to long URL {}", shortCode, longUrl);
            return null; // Return null as we've set the header manually
        } else {
            log.warn("Short code {} not found.", shortCode);
            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 404 Not Found
            return null;
        }
    }
}