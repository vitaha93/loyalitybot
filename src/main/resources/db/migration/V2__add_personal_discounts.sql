-- Personal discounts table
CREATE TABLE IF NOT EXISTS personal_discounts (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    discount_percent INTEGER NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    reason VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_personal_discounts_customer ON personal_discounts(customer_id);
CREATE INDEX idx_personal_discounts_active ON personal_discounts(active);
CREATE INDEX idx_personal_discounts_valid_dates ON personal_discounts(valid_from, valid_until);
