CREATE TABLE feedback_image_quota(id integer PRIMARY KEY CHECK(id=1));
INSERT INTO feedback_image_quota(id) VALUES(1);
CREATE TABLE feedback_image (
 id varchar(36) PRIMARY KEY, feedback_id varchar(36) NOT NULL REFERENCES feedback(id),
 content bytea NOT NULL, size_bytes integer NOT NULL CHECK(size_bytes>0 AND size_bytes<=1000000),
 created timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX feedback_image_oldest ON feedback_image(created,id);
CREATE INDEX feedback_image_report ON feedback_image(feedback_id);
CREATE TABLE feedback_thanks (
 feedback_id varchar(36) PRIMARY KEY REFERENCES feedback(id),
 state varchar(12) NOT NULL DEFAULT 'pending' CHECK(state IN ('pending','sent','skipped')),
 attempts integer NOT NULL DEFAULT 0,
 next_attempt timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 sent_at timestamp with time zone
);
