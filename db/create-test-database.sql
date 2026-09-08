-- Run once as a MySQL administrator. Do not use this database for development data.
CREATE DATABASE IF NOT EXISTS lexi_test_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON lexi_test_db.* TO 'lexi_user'@'localhost';
FLUSH PRIVILEGES;
