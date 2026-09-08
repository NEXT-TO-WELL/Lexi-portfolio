import { expect, test } from "@playwright/test";
import { removeTestUser, removeTestWordTag } from "./support/test-database.mjs";

const password = "e2e-password-123";

test("signed-in user can post a new word and tag", async ({ page }, testInfo) => {
  const suffix = `${Date.now()}-${testInfo.workerIndex}`;
  const userId = `e2e-post-${suffix}`;
  const wordName = `E2EWord${suffix}`;
  const tagName = `E2ETag${suffix}`;

  try {
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E Post User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();
    await page.getByRole("button", { name: "確認" }).click();

    await page.goto("/post");
    await page.getByPlaceholder("言葉を入力または選択").fill(wordName);
    await page.getByPlaceholder("タグを入力または選択").fill(tagName);
    await page.getByPlaceholder("ひらがな・カタカナで読みを入力").fill("てすと");
    await page.reload();
    await expect(page.getByPlaceholder("言葉を入力または選択")).toHaveValue(wordName);
    await expect(page.getByPlaceholder("タグを入力または選択")).toHaveValue(tagName);
    await expect(page.getByPlaceholder("ひらがな・カタカナで読みを入力")).toHaveValue("てすと");
    await page.getByRole("button", { name: "投稿する" }).click();
    await expect(page.getByRole("heading", { name: "投稿の確認" })).toBeVisible();
    await page.getByRole("button", { name: "はい" }).click();

    await expect(page.getByText("登録しました")).toBeVisible();
    await expect(page).toHaveURL(/\/word\/\d+\/tag\/\d+\/(meaning|example|thread)$/);
    await expect(page.getByText(wordName, { exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem("lexi:draft:v1:post"))).toBeNull();
  } finally {
    await removeTestWordTag(wordName, tagName);
    await removeTestUser(userId);
  }
});
