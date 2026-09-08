import { expect, test } from "@playwright/test";

test("guest can continue from sign-in to the home page", async ({ page }) => {
  await page.goto("/signIn");

  await page.getByRole("button", { name: "ゲストユーザーで閲覧する" }).click();

  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole("heading", { name: "今日のトレンド" })).toBeVisible();
});

test.describe("sign-up validation", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
  });

  test("shows an error when passwords do not match", async ({ page }) => {
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E User");
    await page.getByPlaceholder("ログインIDを入力").fill("e2e-user");
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill("correct-password");
    await page.getByPlaceholder("もう一度パスワードを入力").fill("different-password");

    await page.getByRole("button", { name: "アカウント作成" }).click();

    await expect(page.getByRole("heading", { name: "エラー" })).toBeVisible();
    await expect(page.getByText("パスワードが一致しません")).toBeVisible();
  });

  test("shows an error when the password is too short", async ({ page }) => {
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E User");
    await page.getByPlaceholder("ログインIDを入力").fill("e2e-user");
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill("short");
    await page.getByPlaceholder("もう一度パスワードを入力").fill("short");

    await page.getByRole("button", { name: "アカウント作成" }).click();

    await expect(page.getByRole("heading", { name: "エラー" })).toBeVisible();
    await expect(page.getByText("パスワードは8文字以上で入力してください")).toBeVisible();
  });
});
