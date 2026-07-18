ALTER TABLE programs
    ADD COLUMN price DECIMAL(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE programs
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'VND';

ALTER TABLE programs
    ADD COLUMN sales_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

UPDATE programs
SET sales_status = 'PUBLISHED'
WHERE name IN ('English A1', 'English A2');
