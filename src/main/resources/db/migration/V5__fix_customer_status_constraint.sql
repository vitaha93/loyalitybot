-- Fix customers_status_check constraint to include PENDING_BIRTHDAY status
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_status_check;

ALTER TABLE customers ADD CONSTRAINT customers_status_check
    CHECK (status IN ('PENDING_PHONE', 'PENDING_BIRTHDAY', 'ACTIVE', 'BLOCKED'));
