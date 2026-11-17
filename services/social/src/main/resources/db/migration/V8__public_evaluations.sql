ALTER TABLE library ADD COLUMN evaluation_public boolean DEFAULT false NOT NULL;
CREATE INDEX library_public_title ON library(title,evaluation_public,hidden);
