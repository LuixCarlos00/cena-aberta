CREATE TABLE title (
 id varchar(90) PRIMARY KEY, format varchar(5) NOT NULL, anime boolean NOT NULL,
 name varchar(500) NOT NULL, original varchar(500), release_year varchar(4),
 summary text, poster varchar(300)
);
