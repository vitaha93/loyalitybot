-- Add birthday and is_new_client fields to customers table
ALTER TABLE customers ADD COLUMN IF NOT EXISTS birthday DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS is_new_client BOOLEAN DEFAULT false;
