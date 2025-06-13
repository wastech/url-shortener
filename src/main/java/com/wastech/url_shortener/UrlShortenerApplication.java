package com.wastech.url_shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableKafka
public class UrlShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerApplication.class, args);
	}

}
