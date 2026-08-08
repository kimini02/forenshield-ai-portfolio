-- case_profiles.review_status is NOT NULL in RDS but was missing from the JPA entity.
-- Default NONE so empty-case / evidence registration inserts succeed.

ALTER TABLE case_profiles ADD COLUMN IF NOT EXISTS review_status VARCHAR(30);
UPDATE case_profiles SET review_status = 'NONE' WHERE review_status IS NULL;
ALTER TABLE case_profiles ALTER COLUMN review_status SET DEFAULT 'NONE';
ALTER TABLE case_profiles ALTER COLUMN review_status SET NOT NULL;
