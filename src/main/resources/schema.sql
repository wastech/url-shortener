CREATE TABLE IF NOT EXISTS shortened_urls (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(10) NOT NULL UNIQUE,
    long_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    click_count BIGINT DEFAULT 0,
    expires_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_short_code ON shortened_urls (short_code);
CREATE INDEX IF NOT EXISTS idx_long_url ON shortened_urls (long_url);