CREATE TABLE music_search_history (
    id UUID PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL,
    telegram_chat_id BIGINT NOT NULL,
    telegram_username VARCHAR(255),
    telegram_file_id VARCHAR(512) NOT NULL,
    telegram_file_unique_id VARCHAR(512),
    media_type VARCHAR(32) NOT NULL,
    original_file_name VARCHAR(512),
    content_type VARCHAR(255),
    file_size BIGINT,
    minio_object_name VARCHAR(1024),
    status VARCHAR(32) NOT NULL,
    title VARCHAR(512),
    artist VARCHAR(512),
    album VARCHAR(512),
    release_date VARCHAR(64),
    label_name VARCHAR(512),
    timecode VARCHAR(64),
    song_link VARCHAR(2048),
    spotify_url VARCHAR(2048),
    apple_music_url VARCHAR(2048),
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_music_history_user_created
    ON music_search_history (telegram_user_id, created_at DESC);

CREATE INDEX idx_music_history_status
    ON music_search_history (status);
