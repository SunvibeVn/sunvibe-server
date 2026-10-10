-- Registration only requires full name, username (phone number) and password;
-- the ID card number can be added to the profile later, so it becomes optional.
-- Uniqueness among non-deleted users is still enforced by the partial unique
-- index idx_users_id_card_number_hash, which allows any number of NULLs.
ALTER TABLE users ALTER COLUMN id_card_number DROP NOT NULL;
ALTER TABLE users ALTER COLUMN id_card_number_hash DROP NOT NULL;
