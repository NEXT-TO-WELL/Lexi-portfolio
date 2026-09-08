import { expect, test } from "@playwright/test";
import { removeTestUser } from "./support/test-database.mjs";

const password = "e2e-password-123";

test("registered user can sign in and reach the home page", async ({ page }, testInfo) => {
  const userId = `e2e-${Date.now()}-${testInfo.workerIndex}`;

  try {
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E Login User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();

    await expect(page.getByRole("heading", { name: "成功" })).toBeVisible();
    await page.getByRole("button", { name: "確認" }).click();
    await expect(page).toHaveURL(/\/home$/);

    await page.evaluate(() => window.localStorage.clear());
    await page.goto("/signIn");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力").fill(password);
    await page.getByRole("button", { name: "ログイン" }).click();

    await expect(page.getByRole("heading", { name: "成功" })).toBeVisible();
    await page.getByRole("button", { name: "確認" }).click();
    await expect(page).toHaveURL(/\/home$/);
    await expect(page.getByRole("heading", { name: "今日のトレンド" })).toBeVisible();
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem("auth_user_id"))).toBe(userId);
  } finally {
    await removeTestUser(userId);
  }
});
