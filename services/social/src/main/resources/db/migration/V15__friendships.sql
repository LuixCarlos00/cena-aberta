CREATE TABLE friendship (
 id varchar(36) PRIMARY KEY,
 requester varchar(160) NOT NULL REFERENCES profile(actor) ON DELETE CASCADE,
 recipient varchar(160) NOT NULL REFERENCES profile(actor) ON DELETE CASCADE,
 status varchar(12) NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted')),
 created timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(requester <> recipient), UNIQUE(requester,recipient)
);
CREATE INDEX friendship_recipient ON friendship(recipient,status);
