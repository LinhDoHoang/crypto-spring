UPDATE users
SET version = 0
where version IS NULL;

ALTER TABLE users
 ALTER COLUMN version SET DEFAULT 0,
 ALTER COLUMN version SET NOT NULL;