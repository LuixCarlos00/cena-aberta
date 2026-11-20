CREATE TABLE movie_room (
 id varchar(36) PRIMARY KEY, owner varchar(160) NOT NULL REFERENCES profile(actor),
 name varchar(80) NOT NULL, status varchar(12) NOT NULL DEFAULT 'lobby',
 chosen varchar(90), created timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires timestamp with time zone NOT NULL
);
CREATE TABLE movie_room_member (
 room varchar(36) REFERENCES movie_room(id) ON DELETE CASCADE,
 actor varchar(160) REFERENCES profile(actor), joined timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(room,actor)
);
CREATE TABLE movie_room_title (
 room varchar(36) REFERENCES movie_room(id) ON DELETE CASCADE,
 title varchar(90), position integer NOT NULL, snapshot text NOT NULL,
 PRIMARY KEY(room,title), UNIQUE(room,position)
);
CREATE TABLE movie_room_vote (
 room varchar(36), actor varchar(160), title varchar(90), liked boolean NOT NULL,
 PRIMARY KEY(room,actor,title),
 FOREIGN KEY(room,actor) REFERENCES movie_room_member(room,actor) ON DELETE CASCADE,
 FOREIGN KEY(room,title) REFERENCES movie_room_title(room,title) ON DELETE CASCADE
);
CREATE INDEX movie_room_member_actor ON movie_room_member(actor);
