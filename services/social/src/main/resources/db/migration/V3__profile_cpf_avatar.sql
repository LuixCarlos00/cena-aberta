ALTER TABLE profile ADD COLUMN cpf varchar(11);
ALTER TABLE profile ADD COLUMN avatar text;
ALTER TABLE profile ADD CONSTRAINT unique_profile_cpf UNIQUE(cpf);
