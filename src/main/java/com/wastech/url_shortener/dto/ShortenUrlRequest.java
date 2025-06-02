// src/main/java/com/wastech/url_shortener/dto/ShortenUrlRequest.java
package com.wastech.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL; // Important: ensure this import is correct

import lombok.Data; // Assuming you have Lombok
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {

    @NotBlank(message = "Long URL cannot be empty")
//    @URL(message = "Invalid URL format") // Using standard Hibernate @URL validator
    private String longUrl;
}