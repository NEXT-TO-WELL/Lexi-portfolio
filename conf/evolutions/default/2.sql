# --- !Ups

-- Adopt databases created before Play Evolutions became authoritative.
-- Dynamic ALTER statements keep this migration safe when a column already exists.
SET @lexi_has_display_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'Users'
    AND COLUMN_NAME = 'display_id'
);
SET @lexi_sql := IF(
  @lexi_has_display_id = 0,
  'ALTER TABLE Users ADD COLUMN display_id VARCHAR(32) NULL AFTER user_id',
  'SELECT 1'
);
PREPARE lexi_stmt FROM @lexi_sql;
EXECUTE lexi_stmt;
DEALLOCATE PREPARE lexi_stmt;

UPDATE Users
SET display_id = LEFT(REPLACE(UUID(), '-', ''), 12)
WHERE display_id IS NULL OR display_id = '';

ALTER TABLE Users
  MODIFY display_id VARCHAR(32) NOT NULL;

SET @lexi_has_unique_display_id := (
  SELECT COUNT(*)
  FROM (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'Users'
      AND NON_UNIQUE = 0
    GROUP BY INDEX_NAME
    HAVING COUNT(*) = 1
      AND MAX(COLUMN_NAME = 'display_id') = 1
  ) AS unique_display_indexes
);
SET @lexi_sql := IF(
  @lexi_has_unique_display_id = 0,
  'ALTER TABLE Users ADD UNIQUE INDEX uq_users_display_id (display_id)',
  'SELECT 1'
);
PREPARE lexi_stmt FROM @lexi_sql;
EXECUTE lexi_stmt;
DEALLOCATE PREPARE lexi_stmt;

SET @lexi_has_registration_date := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'Users'
    AND COLUMN_NAME = 'user_registration_date'
);
SET @lexi_sql := IF(
  @lexi_has_registration_date = 0,
  'ALTER TABLE Users ADD COLUMN user_registration_date DATETIME NULL DEFAULT CURRENT_TIMESTAMP AFTER role_id',
  'SELECT 1'
);
PREPARE lexi_stmt FROM @lexi_sql;
EXECUTE lexi_stmt;
DEALLOCATE PREPARE lexi_stmt;

UPDATE Users
SET user_registration_date = CURRENT_TIMESTAMP
WHERE user_registration_date IS NULL;

ALTER TABLE Users
  MODIFY user_registration_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE Meanings
  MODIFY meaning_text VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE Examples
  MODIFY example_text VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

# --- !Downs

-- This reconciliation only widens columns and fills missing production data.
-- It is intentionally irreversible; revision 1 removes the full schema on cleanup.
SELECT 1;
