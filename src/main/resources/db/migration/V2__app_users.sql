CREATE TABLE app_users (
  username varchar(64) PRIMARY KEY CHECK (length(trim(username)) >= 3),
  password_hash varchar(64) NOT NULL CHECK (length(password_hash) = 64),
  created timestamp NOT NULL DEFAULT LOCALTIMESTAMP
);