-- Drop customers_status_check constraint completely
-- This is needed because Hibernate might have recreated it with old enum values
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'customers_status_check'
        AND table_name = 'customers'
    ) THEN
        ALTER TABLE customers DROP CONSTRAINT customers_status_check;
    END IF;
END $$;
