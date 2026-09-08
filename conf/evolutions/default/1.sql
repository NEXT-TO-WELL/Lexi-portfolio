# --- !Ups

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS Roles (
    role_id VARCHAR(20) PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS Users (
	user_id CHAR(50) PRIMARY KEY,
	display_id VARCHAR(32) NOT NULL UNIQUE,
	user_pass_hash VARCHAR(255) NOT NULL,
	user_name VARCHAR(30) NOT NULL,
    role_id VARCHAR(20) NOT NULL DEFAULT 'user',
    user_registration_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES Roles(role_id)
);

CREATE TABLE IF NOT EXISTS Words (
	word_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	word_main VARCHAR(100) NOT NULL
);


CREATE TABLE IF NOT EXISTS Tags (
	tag_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	tag_name VARCHAR(50) NOT NULL
);


CREATE TABLE IF NOT EXISTS Thread_Types (
	thread_type_id CHAR(6) PRIMARY KEY,
	thread_type_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Content_Types (
	content_type_id CHAR(6) PRIMARY KEY,
	content_type_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Report_Types (
	report_type_id CHAR(6) PRIMARY KEY,
	report_type_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Report_Status (
	report_status_id CHAR(6) PRIMARY KEY,
	report_status_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Notification_Types (
	notification_types_id CHAR(6) PRIMARY KEY,
	notification_types_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Word_Tags (
	word_id BIGINT,
	tag_id BIGINT,
	user_id CHAR(50),
	word_create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	word_reading VARCHAR(100) NOT NULL,

	PRIMARY KEY(word_id, tag_id),
	CONSTRAINT FK_Word_Tags_Words FOREIGN KEY (word_id) REFERENCES Words(word_id),
	CONSTRAINT FK_Word_Tags_Tags FOREIGN KEY (tag_id) REFERENCES Tags(tag_id),
	CONSTRAINT FK_Word_Tags_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Meanings (
	meaning_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	user_id CHAR(50),
	meaning_posted_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	meaning_text VARCHAR(1000) NOT NULL,
	is_joke BOOLEAN NOT NULL DEFAULT FALSE,
	meaning_good_count INT NOT NULL DEFAULT 0,
	meaning_helpful_count INT NOT NULL DEFAULT 0,
	meaning_funny_count INT NOT NULL DEFAULT 0,
	is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
	last_modified DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,

	CONSTRAINT FK_Meanings_Word_Tags FOREIGN KEY (word_id,tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE,
	CONSTRAINT FK_Meanings_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Examples (
	example_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	user_id CHAR(50),
	example_posted_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	example_text VARCHAR(1000) NOT NULL,
	is_joke BOOLEAN NOT NULL DEFAULT FALSE,
	example_good_count INT NOT NULL DEFAULT 0,
	example_helpful_count INT NOT NULL DEFAULT 0,
	example_funny_count INT NOT NULL DEFAULT 0,
	is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
	last_modified DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,

	CONSTRAINT FK_Examples_Word_Tags FOREIGN KEY (word_id,tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE,
	CONSTRAINT FK_Examples_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Trend_Hot (
	hot_rank INT PRIMARY KEY,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	comment_count INT NOT NULL,

	CONSTRAINT FK_Hot_Word_Tags FOREIGN KEY (word_id,tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE
);


CREATE TABLE IF NOT EXISTS Trend_New (
	new_rank INT PRIMARY KEY,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	good_count INT NOT NULL,

	CONSTRAINT FK_New_Word_Tags FOREIGN KEY (word_id,tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Trend_Buzz (
	buzz_rank INT PRIMARY KEY,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	good_count INT NOT NULL,

	CONSTRAINT FK_Buzz_Word_Tags FOREIGN KEY (word_id,tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Reaction_Good (
	good_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	reaction_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,

	CONSTRAINT UQ_Good_user_content UNIQUE (user_id, content_type_id, content_id),

	CONSTRAINT FK_Good_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Good_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Reaction_Helpful (
	helpful_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	reaction_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,

	CONSTRAINT UQ_Helpful_user_content UNIQUE (user_id, content_type_id, content_id),

	CONSTRAINT FK_Helpful_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Helpful_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Reaction_Funny (
	funny_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	reaction_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,

	CONSTRAINT UQ_Funny_user_content UNIQUE (user_id, content_type_id, content_id),

	CONSTRAINT FK_Funny_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Funny_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Threads (
	thread_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	user_id CHAR(50),
	thread_type_id CHAR(6) NOT NULL,
	thread_create_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	thread_title VARCHAR(100) NOT NULL,
	thread_comment_count INT NOT NULL DEFAULT 0,
	is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
	last_modified DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,

	CONSTRAINT FK_Threads_Word_Tags FOREIGN KEY (word_id,tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE,
	CONSTRAINT FK_Threads_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
	CONSTRAINT FK_Threads_Tread_Types FOREIGN KEY (thread_type_id) REFERENCES Thread_Types(thread_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Comments (
	comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	thread_id BIGINT NOT NULL,
	user_id CHAR(50),
	parent_comment_id BIGINT,
	comment_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	comment_text VARCHAR(400) NOT NULL,
	comment_good_count INT NOT NULL DEFAULT 0,
	comment_bad_count INT NOT NULL DEFAULT 0,
	is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
	last_modified DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,

	CONSTRAINT FK_Comments_Threads FOREIGN KEY (thread_id) REFERENCES Threads(thread_id),
	CONSTRAINT FK_Comments_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
	CONSTRAINT FK_Comments_Comments FOREIGN KEY (parent_comment_id) REFERENCES Comments(comment_id)
);

CREATE TABLE IF NOT EXISTS Thread_Questions (
	thread_id BIGINT PRIMARY KEY,
	is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
	resolved_date DATETIME,
	best_comment_id BIGINT,

	CONSTRAINT FK_Questions_Threads FOREIGN KEY (thread_id) REFERENCES Threads(thread_id),
	CONSTRAINT FK_Questions_Comments FOREIGN KEY (best_comment_id) REFERENCES Comments(comment_id) ON DELETE SET NULL
);


CREATE TABLE IF NOT EXISTS Comment_Reaction (
	comment_reaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50),
	comment_id BIGINT NOT NULL,
	is_good BOOLEAN NOT NULL,
	comment_reaction_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	last_modified DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,

	UNIQUE KEY UQ_user_comment (user_id, comment_id),
	CONSTRAINT FK_Comments_Reaction_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
	CONSTRAINT FK_Comments_Reaction_Comments FOREIGN KEY (comment_id) REFERENCES Comments(comment_id)
);

CREATE TABLE IF NOT EXISTS Hidden_User (
	user_id CHAR(50) NOT NULL,
	target_user_id CHAR(50) NOT NULL,
	hidden_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

	PRIMARY KEY (user_id, target_user_id),
	CONSTRAINT FK_Hidden_Self FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Hidden_Target FOREIGN KEY (target_user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Favorite (
	favorite_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	favorite_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

	UNIQUE KEY UQ_user_content (user_id, content_type_id, content_id),
	CONSTRAINT FK_Favorite_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Favorite_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Follows (
	follow_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	tag_id BIGINT NOT NULL,
	word_id BIGINT,
	is_word_contained BOOLEAN NOT NULL DEFAULT FALSE,
	follow_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	word_id_uniq_helper BIGINT AS (IFNULL(word_id, -1)) VIRTUAL,

	UNIQUE KEY UQ_user_follow (user_id, tag_id, word_id_uniq_helper),
	CONSTRAINT FK_Follows_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Follows_Tags FOREIGN KEY (tag_id) REFERENCES Tags(tag_id),
	CONSTRAINT FK_Follows_Words FOREIGN KEY (word_id) REFERENCES Words(word_id)
);

CREATE TABLE IF NOT EXISTS Search_histories (
	search_histry_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	search_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	search_history_text VARCHAR(500),

	INDEX IDX_user_search (user_id, search_date DESC),

	CONSTRAINT FK_Search_histories_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS View_histories (
	user_id CHAR(50) NOT NULL,
	word_id BIGINT NOT NULL,
	tag_id BIGINT NOT NULL,
	view_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

	PRIMARY KEY (user_id, word_id, tag_id),
	INDEX IDX_user_view (user_id, view_date DESC),

	CONSTRAINT FK_View_histories_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_View_histories_Word_Tags FOREIGN KEY (word_id, tag_id) REFERENCES Word_Tags(word_id, tag_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Deleted_Users (
	deleted_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	user_name VARCHAR(30) NOT NULL,
	delete_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	delete_reason VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS Login_Log (
	user_id CHAR(50) NOT NULL,
	login_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	logout_date DATETIME DEFAULT NULL,

	PRIMARY KEY (user_id, login_date),

	CONSTRAINT FK_Login_Log_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Notifications (
	notifications_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	notifications_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	is_read BOOLEAN NOT NULL DEFAULT FALSE,
	notification_types_id CHAR(6) NOT NULL,

	CONSTRAINT FK_Notifications_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Notifications_Notification_Types FOREIGN KEY (notification_types_id) REFERENCES Notification_Types(notification_types_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS System_Notifications (
	notifications_id BIGINT PRIMARY KEY,
	notifications_text VARCHAR(300) NOT NULL,

	CONSTRAINT FK_System_Notifications FOREIGN KEY (notifications_id) REFERENCES Notifications(notifications_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Reaction_Notifications (
	notifications_id BIGINT PRIMARY KEY,
	action_user_id CHAR(50) NOT NULL,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	reaction_id BIGINT NOT NULL,

	CONSTRAINT FK_Reaction_Notifications FOREIGN KEY (notifications_id) REFERENCES Notifications(notifications_id) ON DELETE CASCADE,
	CONSTRAINT FK_Reaction_Notifications_Users FOREIGN KEY (action_user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Reaction_Notifications_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Follow_Notifications (
	notifications_id BIGINT PRIMARY KEY,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	follow_type ENUM('TAG','WORD') NOT NULL,

	CONSTRAINT FK_Follow_Notifications FOREIGN KEY (notifications_id) REFERENCES Notifications(notifications_id) ON DELETE CASCADE,
	CONSTRAINT FK_Follow_Notifications_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS Comment_Notifications (
	notifications_id BIGINT PRIMARY KEY,
	thread_id BIGINT NOT NULL,
	reply_comment_id BIGINT NOT NULL,
	reply_to_comment_id BIGINT,

	CONSTRAINT FK_Comment_Notifications FOREIGN KEY (notifications_id) REFERENCES Notifications(notifications_id) ON DELETE CASCADE,
	CONSTRAINT FK_Comment_Notifications_Threads FOREIGN KEY (thread_id) REFERENCES Threads(thread_id),
	CONSTRAINT FK_Comment_Notifications_Reply_Comment FOREIGN KEY (reply_comment_id) REFERENCES Comments(comment_id),
	CONSTRAINT FK_Comment_Notifications_To_Comment FOREIGN KEY (reply_to_comment_id) REFERENCES Comments(comment_id)
);


CREATE TABLE IF NOT EXISTS Reports (
	report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
	user_id CHAR(50) NOT NULL,
	report_type_id CHAR(6) NOT NULL,
	report_status_id CHAR(6) NOT NULL,
	content_type_id CHAR(6) NOT NULL,
	content_id BIGINT NOT NULL,
	report_date DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,

	CONSTRAINT FK_Reports_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
	CONSTRAINT FK_Reports_Report_Types FOREIGN KEY (report_type_id) REFERENCES Report_Types(report_type_id) ON UPDATE CASCADE,
	CONSTRAINT FK_Reports_Report_Status FOREIGN KEY (report_status_id) REFERENCES Report_Status(report_status_id) ON UPDATE CASCADE,
	CONSTRAINT FK_Reports_Content_Types FOREIGN KEY (content_type_id) REFERENCES Content_Types(content_type_id) ON UPDATE CASCADE
);

INSERT IGNORE INTO Roles (role_id, role_name, description) VALUES
('admin', '管理者', 'システム管理者'),
('user', '一般ユーザー', '通常の利用者');

INSERT IGNORE INTO Content_Types (content_type_id, content_type_name) VALUES
('1', '語釈'),
('2', '用例'),
('3', 'スレッド'),
('4', 'コメント');

INSERT IGNORE INTO Thread_Types (thread_type_id, thread_type_name) VALUES
('1', '議論'),
('2', '質問'),
('3', '雑談'),
('4', '共有');

INSERT IGNORE INTO Report_Types (report_type_id, report_type_name) VALUES
('1', 'スパム'),
('2', '不適切なコンテンツ'),
('3', '誹謗中傷'),
('4', 'その他');

INSERT IGNORE INTO Report_Status (report_status_id, report_status_name) VALUES
('1', '未対応'),
('2', '対応中'),
('3', '対応済み'),
('4', '却下');

INSERT IGNORE INTO Notification_Types (notification_types_id, notification_types_name) VALUES
('1', 'システム通知'),
('2', 'リアクション通知'),
('3', 'フォロー通知'),
('4', 'コメント通知');

# --- !Downs

DROP TABLE IF EXISTS Reports;
DROP TABLE IF EXISTS Comment_Notifications;
DROP TABLE IF EXISTS Follow_Notifications;
DROP TABLE IF EXISTS Reaction_Notifications;
DROP TABLE IF EXISTS System_Notifications;
DROP TABLE IF EXISTS Notifications;
DROP TABLE IF EXISTS Login_Log;
DROP TABLE IF EXISTS Deleted_Users;
DROP TABLE IF EXISTS View_histories;
DROP TABLE IF EXISTS Search_histories;
DROP TABLE IF EXISTS Follows;
DROP TABLE IF EXISTS Favorite;
DROP TABLE IF EXISTS Hidden_User;
DROP TABLE IF EXISTS Comment_Reaction;
DROP TABLE IF EXISTS Thread_Questions;
DROP TABLE IF EXISTS Comments;
DROP TABLE IF EXISTS Threads;
DROP TABLE IF EXISTS Reaction_Funny;
DROP TABLE IF EXISTS Reaction_Helpful;
DROP TABLE IF EXISTS Reaction_Good;
DROP TABLE IF EXISTS Trend_Buzz;
DROP TABLE IF EXISTS Trend_New;
DROP TABLE IF EXISTS Trend_Hot;
DROP TABLE IF EXISTS Examples;
DROP TABLE IF EXISTS Meanings;
DROP TABLE IF EXISTS Word_Tags;
DROP TABLE IF EXISTS Notification_Types;
DROP TABLE IF EXISTS Report_Status;
DROP TABLE IF EXISTS Report_Types;
DROP TABLE IF EXISTS Content_Types;
DROP TABLE IF EXISTS Thread_Types;
DROP TABLE IF EXISTS Tags;
DROP TABLE IF EXISTS Words;
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Roles;
