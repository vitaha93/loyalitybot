-- Customers table
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL UNIQUE,
    telegram_username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone VARCHAR(20),
    poster_client_id BIGINT UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PHONE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_telegram_id ON customers(telegram_id);
CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_poster_client_id ON customers(poster_client_id);
CREATE INDEX idx_customers_status ON customers(status);

-- Bonus notifications table
CREATE TABLE bonus_notifications (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    bonus_amount DECIMAL(10, 2) NOT NULL,
    transaction_id VARCHAR(255),
    message TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    next_retry_at TIMESTAMP
);

CREATE INDEX idx_bonus_notifications_customer_id ON bonus_notifications(customer_id);
CREATE INDEX idx_bonus_notifications_status ON bonus_notifications(status);
CREATE INDEX idx_bonus_notifications_next_retry_at ON bonus_notifications(next_retry_at);

-- Broadcast jobs table
CREATE TABLE broadcast_jobs (
    id BIGSERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    image_file_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_recipients INT NOT NULL DEFAULT 0,
    sent_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_broadcast_jobs_status ON broadcast_jobs(status);
CREATE INDEX idx_broadcast_jobs_created_at ON broadcast_jobs(created_at);
