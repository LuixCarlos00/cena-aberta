CREATE TABLE episode_rating (
 actor varchar(160) NOT NULL REFERENCES profile(actor) ON DELETE CASCADE,
 title varchar(90) NOT NULL,
 season integer NOT NULL,
 episode integer NOT NULL,
 rating integer NOT NULL CHECK (rating BETWEEN 1 AND 10),
 PRIMARY KEY(actor,title,season,episode)
);
