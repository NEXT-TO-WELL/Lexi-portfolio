# --- !Ups

ALTER TABLE Comment_Notifications
  ADD COLUMN notification_kind VARCHAR(32) NOT NULL DEFAULT 'COMMENT' AFTER reply_to_comment_id,
  ADD CONSTRAINT CK_Comment_Notifications_kind
    CHECK (notification_kind IN ('COMMENT', 'BEST_ANSWER'));

# --- !Downs

ALTER TABLE Comment_Notifications
  DROP CHECK CK_Comment_Notifications_kind,
  DROP COLUMN notification_kind;
