ALTER TABLE email_account ADD COLUMN state varchar(10) NOT NULL DEFAULT 'active' CHECK (state IN ('active','paused','deleted'));
ALTER TABLE email_account ADD COLUMN state_changed_at timestamp with time zone;
