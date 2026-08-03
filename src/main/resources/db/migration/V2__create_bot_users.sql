CREATE TABLE bot_users (
    telegram_user_id BIGINT PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL,
    telegram_username VARCHAR(255),
    first_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
