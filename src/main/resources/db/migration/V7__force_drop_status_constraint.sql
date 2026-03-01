-- Force drop the constraint - it was recreated by Hibernate before ddl-auto was disabled
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_status_check;
