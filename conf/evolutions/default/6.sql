# --- !Ups

ALTER TABLE Users
  ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER user_registration_date;

# --- !Downs

ALTER TABLE Users
  DROP COLUMN token_version;
