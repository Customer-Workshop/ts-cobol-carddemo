-- Corrective migration for V2601's entity drift (invisible on H2 where
-- Flyway is disabled): auth_frauds key columns mapped Long, both tables'
-- pos_entry_mode mapped Integer, and pending_auth_detail's implicit column
-- names for authDate9c/authTime9c carry no underscore before the suffix.
ALTER TABLE auth_frauds ALTER COLUMN acct_id TYPE bigint;
ALTER TABLE auth_frauds ALTER COLUMN cust_id TYPE bigint;
ALTER TABLE auth_frauds ALTER COLUMN pos_entry_mode TYPE integer;
ALTER TABLE pending_auth_detail ALTER COLUMN pos_entry_mode TYPE integer;
ALTER TABLE pending_auth_detail RENAME COLUMN auth_date_9c TO auth_date9c;
ALTER TABLE pending_auth_detail RENAME COLUMN auth_time_9c TO auth_time9c;
