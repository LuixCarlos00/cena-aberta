ALTER TABLE profile ADD COLUMN ranking_version integer NOT NULL DEFAULT 0;
CREATE TABLE film_ranking (
 actor varchar(160) NOT NULL REFERENCES profile(actor) ON DELETE CASCADE,
 title varchar(90) NOT NULL,
 tier integer NOT NULL CHECK(tier BETWEEN 0 AND 4),
 position integer NOT NULL,
 PRIMARY KEY(actor,title),
 UNIQUE(actor,tier,position)
);
