ALTER TABLE profile ADD COLUMN show_watchlist boolean NOT NULL DEFAULT false;
ALTER TABLE profile ADD COLUMN show_ranking boolean NOT NULL DEFAULT false;
ALTER TABLE profile ADD COLUMN show_watched boolean NOT NULL DEFAULT true;
ALTER TABLE profile ADD COLUMN show_diary boolean NOT NULL DEFAULT false;
ALTER TABLE profile ADD COLUMN show_lists boolean NOT NULL DEFAULT true;
