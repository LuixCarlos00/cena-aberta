CREATE TABLE movie_room_invitation (
 room varchar(36) NOT NULL REFERENCES movie_room(id) ON DELETE CASCADE,
 recipient varchar(160) NOT NULL REFERENCES profile(actor) ON DELETE CASCADE,
 sender varchar(160) NOT NULL REFERENCES profile(actor) ON DELETE CASCADE,
 created timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(room,recipient), CHECK(sender<>recipient)
);
CREATE INDEX movie_room_invitation_recipient ON movie_room_invitation(recipient);
