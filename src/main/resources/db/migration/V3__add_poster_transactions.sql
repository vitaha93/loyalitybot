-- Poster transactions table
CREATE TABLE poster_transactions (
    id BIGSERIAL PRIMARY KEY,
    poster_transaction_id BIGINT NOT NULL UNIQUE,
    poster_client_id BIGINT,
    client_first_name VARCHAR(255),
    client_last_name VARCHAR(255),
    transaction_date TIMESTAMP,
    date_close VARCHAR(50),
    sum DECIMAL(19, 2),
    payed_sum DECIMAL(19, 2),
    payed_bonus DECIMAL(19, 2),
    bonus_earned DECIMAL(19, 2),
    discount DECIMAL(19, 2),
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_poster_tx_client_id ON poster_transactions(poster_client_id);
CREATE INDEX idx_poster_tx_date ON poster_transactions(transaction_date);

-- Poster transaction products table
CREATE TABLE poster_transaction_products (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES poster_transactions(id) ON DELETE CASCADE,
    poster_client_id BIGINT,
    poster_product_id BIGINT,
    product_name VARCHAR(255),
    count INTEGER,
    price DECIMAL(19, 2)
);

CREATE INDEX idx_poster_tx_prod_client ON poster_transaction_products(poster_client_id);
CREATE INDEX idx_poster_tx_prod_product ON poster_transaction_products(poster_product_id);

-- Sync status table
CREATE TABLE sync_status (
    id BIGSERIAL PRIMARY KEY,
    sync_type VARCHAR(50) NOT NULL UNIQUE,
    last_sync_date DATE,
    last_sync_at TIMESTAMP,
    records_synced INTEGER
);
