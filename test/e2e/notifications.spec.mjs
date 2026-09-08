import { expect, test } from "@playwright/test";
import {
  createTestWordDetailFixture,
  getUnreadNotificationCount,
  insertTestReactionNotification,
  insertTestNotifications,
  removeTestWordDetailFixture,
  removeTestUser,
} from "./support/test-database.mjs";

const password = "e2e-password-123";

test("signed-in user can mark every notification as read", async ({ page }, testInfo) => {
  const userId = `e2e-notifications-${Date.now()}-${testInfo.workerIndex}`;

  try {
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E Notification User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();
    await page.getByRole("button", { name: "確認" }).click();

    await insertTestNotifications(userId, 2);
    await page.reload();

    await expect(page.locator("#notification-unread-count")).toHaveText("2");
    await page.getByRole("button", { name: "お知らせ", exact: true }).click();
    await expect(page.locator("#push-notification-control")).toContainText(
      "ブラウザ通知は現在利用できません。",
    );
    await expect(page.locator('[data-notification-read="false"]')).toHaveCount(2);
    await expect(page.locator('[data-notification-type="SYSTEM"] svg')).toHaveCount(2);
    await expect(page.getByText("E2Eシステム通知 1", { exact: true })).toBeVisible();
    await expect(page.getByText("E2Eシステム通知 2", { exact: true })).toBeVisible();

    const markAllButton = page.getByRole("button", { name: "すべての通知を既読にする" });
    await expect(markAllButton).toBeEnabled();
    await markAllButton.click();

    await expect(page.locator('[data-notification-read="false"]')).toHaveCount(0);
    await expect(page.locator('[data-notification-read="true"]')).toHaveCount(2);
    await expect(page.locator("#notification-unread-count")).toHaveCount(0);
    await expect(markAllButton).toBeDisabled();
    await expect.poll(() => getUnreadNotificationCount(userId)).toBe(0);

    await page.reload();
    await expect(page.locator("#notification-unread-count")).toHaveCount(0);
    await page.getByRole("button", { name: "お知らせ", exact: true }).click();
    await expect(page.getByRole("button", { name: "すべての通知を既読にする" })).toBeDisabled();
  } finally {
    await removeTestUser(userId);
  }
});

test("push reception refreshes the unread notification count", async ({
  page,
}, testInfo) => {
  const userId = `e2e-notification-push-${Date.now()}-${testInfo.workerIndex}`;

  try {
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page
      .getByPlaceholder("例: Lexiユーザー")
      .fill("E2E Push Refresh User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page
      .getByPlaceholder("パスワードを入力", { exact: true })
      .fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();
    await page.getByRole("button", { name: "確認" }).click();

    await insertTestNotifications(userId, 1);
    await page.evaluate(() => {
      navigator.serviceWorker.dispatchEvent(
        new MessageEvent("message", {
          data: { type: "LEXI_PUSH_RECEIVED", notificationId: "1" },
        }),
      );
    });

    await expect(page.locator("#notification-unread-count")).toHaveText("1");
  } finally {
    await removeTestUser(userId);
  }
});

test("reaction notification opens the referenced word and tag", async ({ page }, testInfo) => {
  const suffix = `${Date.now()}${testInfo.workerIndex}`.slice(-12);
  const userId = `e2e-notification-link-${suffix}`;
  const fixture = await createTestWordDetailFixture(suffix);

  try {
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E Notification Link User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();
    await page.getByRole("button", { name: "確認" }).click();

    await insertTestReactionNotification(userId, fixture.authorId, "1", fixture.meaningId);
    await page.reload();
    await page.getByRole("button", { name: "お知らせ", exact: true }).click();

    const notification = page.locator('[data-notification-type="REACTION"]');
    await expect(notification).toContainText("あなたの語釈にリアクションしました");
    await notification.click();

    await expect(page).toHaveURL(new RegExp(`/word/${fixture.wordId}/tag/${fixture.tagId}/meaning$`));
    await expect.poll(() => getUnreadNotificationCount(userId)).toBe(0);
  } finally {
    await removeTestUser(userId);
    await removeTestWordDetailFixture(fixture);
  }
});
