import mysql from "mysql2/promise";

const fallbackTestUrl =
  "jdbc:mysql://localhost:3306/lexi_test_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Tokyo&characterEncoding=UTF-8";

function testDatabaseConfig() {
  const jdbcUrl = process.env.TEST_DB_URL ?? fallbackTestUrl;
  const url = new URL(jdbcUrl.replace(/^jdbc:/, ""));
  const database = url.pathname.slice(1);

  if (database !== "lexi_test_db") {
    throw new Error(`E2E cleanup only permits lexi_test_db, received ${database || "no database"}.`);
  }

  if (!process.env.DB_USER || !process.env.DB_PASSWORD) {
    throw new Error("DB_USER and DB_PASSWORD must be set before running E2E tests.");
  }

  return {
    host: url.hostname,
    port: Number(url.port || 3306),
    database,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD
  };
}

export async function removeTestUser(userId) {
  const connection = await mysql.createConnection(testDatabaseConfig());

  try {
    await connection.execute("DELETE FROM Login_Log WHERE user_id = ?", [userId]);
    await connection.execute("DELETE FROM Users WHERE user_id = ?", [userId]);
  } finally {
    await connection.end();
  }
}

export async function insertTestNotifications(userId, count) {
  const connection = await mysql.createConnection(testDatabaseConfig());

  try {
    const now = Date.now();
    for (let index = 0; index < count; index += 1) {
      const [result] = await connection.execute(
        `INSERT INTO Notifications
           (user_id, notifications_date, is_read, notification_types_id)
         VALUES (?, ?, false, '1')`,
        [userId, new Date(now + index)],
      );
      await connection.execute(
        `INSERT INTO System_Notifications (notifications_id, notifications_text)
         VALUES (?, ?)`,
        [result.insertId, `E2Eシステム通知 ${index + 1}`],
      );
    }
  } finally {
    await connection.end();
  }
}

export async function insertTestReactionNotification(userId, actionUserId, contentTypeId, contentId) {
  const connection = await mysql.createConnection(testDatabaseConfig());

  try {
    const [result] = await connection.execute(
      `INSERT INTO Notifications
         (user_id, notifications_date, is_read, notification_types_id)
       VALUES (?, NOW(), false, '2')`,
      [userId],
    );
    await connection.execute(
      `INSERT INTO Reaction_Notifications
         (notifications_id, action_user_id, content_type_id, content_id, reaction_id)
       VALUES (?, ?, ?, ?, ?)`,
      [result.insertId, actionUserId, contentTypeId, contentId, result.insertId],
    );
  } finally {
    await connection.end();
  }
}

export async function getUnreadNotificationCount(userId) {
  const connection = await mysql.createConnection(testDatabaseConfig());

  try {
    const [rows] = await connection.execute(
      "SELECT COUNT(*) AS unreadCount FROM Notifications WHERE user_id = ? AND is_read = false",
      [userId],
    );
    return Number(rows[0].unreadCount);
  } finally {
    await connection.end();
  }
}

export async function createTestWordDetailFixture(suffix) {
  const connection = await mysql.createConnection(testDatabaseConfig());
  const authorId = `e2e-author-${suffix}`;
  const wordName = `E2EQuoteWord${suffix}`;
  const tagName = `E2EQuoteTag${suffix}`;

  try {
    await connection.beginTransaction();
    await connection.execute(
      `INSERT INTO Users
         (user_id, display_id, user_name, user_pass_hash, role_id, user_registration_date)
       VALUES (?, ?, ?, 'e2e-fixture', 'user', NOW())`,
      [authorId, `author${suffix}`.slice(0, 32), "E2E引用元ユーザー"],
    );
    const [wordResult] = await connection.execute("INSERT INTO Words (word_main) VALUES (?)", [wordName]);
    const [tagResult] = await connection.execute("INSERT INTO Tags (tag_name) VALUES (?)", [tagName]);
    const wordId = Number(wordResult.insertId);
    const tagId = Number(tagResult.insertId);

    await connection.execute(
      `INSERT INTO Word_Tags (word_id, tag_id, user_id, word_create_date, word_reading)
       VALUES (?, ?, ?, NOW(), 'いんようてすと')`,
      [wordId, tagId, authorId],
    );
    const [meaningResult] = await connection.execute(
      `INSERT INTO Meanings
         (word_id, tag_id, user_id, meaning_posted_date, meaning_text, meaning_good_count,
          meaning_helpful_count, meaning_funny_count, last_modified)
       VALUES (?, ?, ?, NOW(), 'スマホ表示を確認するための語釈です', 123, 45, 6, NOW())`,
      [wordId, tagId, authorId],
    );
    const [exampleResult] = await connection.execute(
      `INSERT INTO Examples
         (word_id, tag_id, user_id, example_posted_date, example_text, example_good_count,
          example_helpful_count, example_funny_count, last_modified)
       VALUES (?, ?, ?, NOW(), 'スマホ表示を確認するための用例です', 123, 45, 6, NOW())`,
      [wordId, tagId, authorId],
    );
    await connection.commit();

    return {
      authorId,
      exampleId: Number(exampleResult.insertId),
      meaningId: Number(meaningResult.insertId),
      tagId,
      wordId,
    };
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    await connection.end();
  }
}

export async function removeTestWordDetailFixture({ authorId, meaningId, tagId, wordId }) {
  const connection = await mysql.createConnection(testDatabaseConfig());

  try {
    await connection.beginTransaction();
    const [threadRows] = await connection.execute(
      "SELECT thread_id AS threadId FROM Threads WHERE word_id = ? AND tag_id = ?",
      [wordId, tagId],
    );
    const threadIds = threadRows.map(({ threadId }) => Number(threadId));

    if (threadIds.length > 0) {
      await connection.query("DELETE FROM Content_Quotes WHERE target_content_type_id = '3' AND target_content_id IN (?)", [
        threadIds,
      ]);
      await connection.query("DELETE FROM Thread_Questions WHERE thread_id IN (?)", [threadIds]);
      await connection.query("DELETE FROM Comments WHERE thread_id IN (?)", [threadIds]);
      await connection.query("DELETE FROM Threads WHERE thread_id IN (?)", [threadIds]);
    }
    await connection.execute(
      "DELETE FROM Content_Quotes WHERE source_content_type_id = '1' AND source_content_id = ?",
      [meaningId],
    );
    await connection.execute("DELETE FROM Meanings WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
    await connection.execute("DELETE FROM Examples WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
    await connection.execute("DELETE FROM View_histories WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
    await connection.execute("DELETE FROM Word_Tags WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
    await connection.execute("DELETE FROM Words WHERE word_id = ?", [wordId]);
    await connection.execute("DELETE FROM Tags WHERE tag_id = ?", [tagId]);
    await connection.execute("DELETE FROM Users WHERE user_id = ?", [authorId]);
    await connection.commit();
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    await connection.end();
  }
}

export async function removeTestWordTag(wordName, tagName) {
  const connection = await mysql.createConnection(testDatabaseConfig());

  try {
    await connection.beginTransaction();
    const [pairs] = await connection.execute(
      `SELECT wt.word_id AS wordId, wt.tag_id AS tagId
       FROM Word_Tags wt
       INNER JOIN Words w ON w.word_id = wt.word_id
       INNER JOIN Tags t ON t.tag_id = wt.tag_id
       WHERE w.word_main = ? AND t.tag_name = ?`,
      [wordName, tagName]
    );

    for (const { wordId, tagId } of pairs) {
      await connection.execute("DELETE FROM View_histories WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
      await connection.execute("DELETE FROM Trend_Hot WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
      await connection.execute("DELETE FROM Trend_New WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
      await connection.execute("DELETE FROM Trend_Buzz WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
      await connection.execute("DELETE FROM Word_Tags WHERE word_id = ? AND tag_id = ?", [wordId, tagId]);
      await connection.execute("DELETE FROM Words WHERE word_id = ?", [wordId]);
      await connection.execute("DELETE FROM Tags WHERE tag_id = ?", [tagId]);
    }

    await connection.commit();
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    await connection.end();
  }
}
