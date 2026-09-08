# --- !Ups

CREATE TABLE Content_Quotes (
  quote_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_content_type_id CHAR(6) NOT NULL,
  source_content_id BIGINT NOT NULL,
  target_content_type_id CHAR(6) NOT NULL,
  target_content_id BIGINT NOT NULL,
  created_by_user_id CHAR(50) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_by_user_id CHAR(50),
  deleted_at DATETIME,

  CONSTRAINT UQ_Content_Quotes_target UNIQUE (target_content_type_id, target_content_id),
  CONSTRAINT FK_Content_Quotes_source_type
    FOREIGN KEY (source_content_type_id) REFERENCES Content_Types(content_type_id),
  CONSTRAINT FK_Content_Quotes_target_type
    FOREIGN KEY (target_content_type_id) REFERENCES Content_Types(content_type_id),
  CONSTRAINT CK_Content_Quotes_source_type CHECK (source_content_type_id IN ('1', '2')),
  CONSTRAINT CK_Content_Quotes_target_type CHECK (target_content_type_id IN ('3', '4')),
  CONSTRAINT CK_Content_Quotes_deleted_audit CHECK (
    (is_deleted = FALSE AND deleted_by_user_id IS NULL AND deleted_at IS NULL)
    OR
    (is_deleted = TRUE AND deleted_by_user_id IS NOT NULL AND deleted_at IS NOT NULL)
  ),
  INDEX IX_Content_Quotes_source (source_content_type_id, source_content_id, is_deleted)
);

# --- !Downs

DROP TABLE IF EXISTS Content_Quotes;
