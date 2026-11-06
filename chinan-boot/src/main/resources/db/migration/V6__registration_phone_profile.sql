ALTER TABLE counselor_registration ADD COLUMN IF NOT EXISTS gender VARCHAR(16);
ALTER TABLE counselor_registration ADD COLUMN IF NOT EXISTS department VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS idx_counselor_registration_phone_status
    ON counselor_registration(phone, status);
