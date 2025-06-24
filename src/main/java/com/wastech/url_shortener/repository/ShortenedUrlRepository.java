package com.wastech.url_shortener.repository;

import com.wastech.url_shortener.model.ShortenedUrl;
import com.wastech.url_shortener.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortenedUrlRepository extends JpaRepository<ShortenedUrl, Long> {
    Optional<ShortenedUrl> findByShortCode(String shortCode);
    Optional<ShortenedUrl> findByLongUrl(String longUrl);
    List<ShortenedUrl> findByUser(User user);
    Optional<ShortenedUrl> findByIdAndUser(Long id, User user);
}