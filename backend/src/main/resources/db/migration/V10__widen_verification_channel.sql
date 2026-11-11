-- V10: 'PASSWORD_RESET' (14 characters) did not fit user_verifications.channel VARCHAR(10), so forgot-password
-- and admin-invitation emails failed with "value too long". The CHECK constraint (EMAIL, PHONE, PASSWORD_RESET) is unchanged.
ALTER TABLE user_verifications ALTER COLUMN channel TYPE VARCHAR(20);
