package com.wastech.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {

    @NotBlank(message = "URL cannot be blank")
    @Pattern(
        regexp = "^https?://(?:[-\\w.])+(?:\\:[0-9]+)?(?:/(?:[\\w/_.])*(?:\\?(?:[\\w&=%.])*)?(?:#(?:[\\w.])*)?)?$",
        message = "Invalid URL format. URL must start with http:// or https://"
    )
    private String longUrl;
}