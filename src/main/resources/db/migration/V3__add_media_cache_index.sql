CREATE INDEX IF NOT EXISTS idx_music_history_unique_media_status_created
    ON music_search_history (
        telegram_file_unique_id,
        media_type,
        status,
        created_at DESC
    );