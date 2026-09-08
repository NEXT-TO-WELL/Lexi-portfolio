# --- !Ups

CREATE TABLE Push_Subscriptions (
  subscription_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(50) NOT NULL,
  endpoint VARCHAR(2048) NOT NULL,
  endpoint_hash CHAR(64) NOT NULL,
  p256dh_key VARCHAR(255) NOT NULL,
  auth_key VARCHAR(255) NOT NULL,
  user_agent VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  failure_count INT NOT NULL DEFAULT 0,
  last_failure_at DATETIME,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at DATETIME,

  CONSTRAINT UQ_Push_Subscriptions_endpoint_hash UNIQUE (endpoint_hash),
  CONSTRAINT FK_Push_Subscriptions_Users
    FOREIGN KEY (user_id) REFERENCES Users(user_id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT CK_Push_Subscriptions_deleted_audit CHECK (
    (is_deleted = FALSE AND deleted_at IS NULL)
    OR
    (is_deleted = TRUE AND deleted_at IS NOT NULL)
  ),
  INDEX IX_Push_Subscriptions_user_active (user_id, is_deleted)
);

# --- !Downs

DROP TABLE IF EXISTS Push_Subscriptions;
