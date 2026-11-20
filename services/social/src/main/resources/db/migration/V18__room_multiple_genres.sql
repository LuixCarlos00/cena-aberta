ALTER TABLE movie_room ADD COLUMN genres varchar(160) NOT NULL DEFAULT '';
UPDATE movie_room SET genres=CAST(genre AS varchar(160)) WHERE genre<>0;
