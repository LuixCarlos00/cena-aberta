CREATE TABLE app_admin(actor varchar(160) PRIMARY KEY REFERENCES profile(actor) ON DELETE CASCADE);
CREATE TABLE feedback (
 id varchar(36) PRIMARY KEY, actor varchar(160) NOT NULL REFERENCES profile(actor),
 kind varchar(12) NOT NULL CHECK(kind IN ('bug','suggestion')),
 description varchar(3000) NOT NULL, page varchar(250) NOT NULL, app_version varchar(80) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'new' CHECK(status IN ('new','reviewing','resolved')),
 created timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX feedback_status_created ON feedback(status,created);
CREATE INDEX feedback_actor_created ON feedback(actor,created);
