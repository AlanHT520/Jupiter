ALTER TABLE counselor ADD COLUMN IF NOT EXISTS password_plain VARCHAR(100);
ALTER TABLE counselor_registration ADD COLUMN IF NOT EXISTS password_plain VARCHAR(100);
