package com.wastech.url_shortener.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyRequest {
    private String shortCode;
    private String longUrl;
    private Long userId;
    private LocalDateTime expiresAt;
}