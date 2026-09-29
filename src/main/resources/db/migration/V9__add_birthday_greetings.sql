-- Birthday greetings sent to customers (one per customer per year)
CREATE TABLE birthday_greetings (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    poster_client_id BIGINT,
    greeting_year INTEGER NOT NULL,
    bonus_amount NUMERIC(10, 2),
    status VARCHAR(30) NOT NULL,  -- 'PENDING', 'SENT', 'SENT_WITHOUT_BONUS', 'FAILED'
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    CONSTRAINT uk_birthday_greetings_customer_year UNIQUE (customer_id, greeting_year)
);

CREATE INDEX idx_birthday_greetings_created_at ON birthday_greetings(created_at);
