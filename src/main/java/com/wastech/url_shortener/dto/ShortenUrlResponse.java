// src/main/java/com/wastech/url_shortener/dto/ShortenUrlResponse.java
package com.wastech.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Import LocalDateTime

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlResponse {
    private String shortCode;
    private String longUrl;
    private Long clickCount;
    private LocalDateTime expiresAt;
    private String message;

}