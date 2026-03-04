CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    sender_type VARCHAR(20) NOT NULL,  -- 'CUSTOMER' or 'ADMIN'
    sender_telegram_id BIGINT NOT NULL,
    message_text TEXT NOT NULL,
    telegram_message_id BIGINT,
    forwarded_message_id BIGINT,
    admin_chat_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP
);

CREATE INDEX idx_chat_messages_customer_id ON chat_messages(customer_id);
CREATE INDEX idx_chat_messages_forwarded ON chat_messages(forwarded_message_id, admin_chat_id);
