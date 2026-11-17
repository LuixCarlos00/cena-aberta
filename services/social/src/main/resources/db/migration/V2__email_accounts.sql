CREATE TABLE email_account (
  actor varchar(160) PRIMARY KEY,
  email varchar(254) NOT NULL UNIQUE,
  password_hash varchar(100) NOT NULL,
  session_version integer NOT NULL DEFAULT 0,
  verified_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE email_link (
  email varchar(254) NOT NULL,
  purpose varchar(10) NOT NULL,
  token_hash varchar(64) NOT NULL UNIQUE,
  sent_at timestamp with time zone NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  PRIMARY KEY(email,purpose)
);
