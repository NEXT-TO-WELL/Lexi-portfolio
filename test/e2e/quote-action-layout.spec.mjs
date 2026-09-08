import { expect, test } from "@playwright/test";
import {
  createTestWordDetailFixture,
  removeTestUser,
  removeTestWordDetailFixture,
} from "./support/test-database.mjs";

const password = "e2e-password-123";

async function expectSingleLineButton(button) {
  await expect(button).toHaveCSS("white-space", "nowrap");
  const box = await button.boundingBox();
  expect(box).not.toBeNull();
  expect(box.height).toBeLessThan(48);
}

test("quote actions remain usable on mobile and desktop", async ({ page }, testInfo) => {
  const suffix = `${Date.now()}-${testInfo.workerIndex}`;
  const userId = `e2e-quote-layout-${suffix}`;
  let fixture;

  try {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E Quote Layout User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();
    await page.getByRole("button", { name: "確認" }).click();

    fixture = await createTestWordDetailFixture(suffix);
    await page.goto(`/word/${fixture.wordId}/tag/${fixture.tagId}/meaning`);
    const meaningCard = page.locator(`#meaning-${fixture.meaningId}`);
    await expect(meaningCard).toBeVisible();

    const reactionButtons = meaningCard.locator('[data-reaction]');
    await expect(reactionButtons).toHaveCount(3);
    for (const button of await reactionButtons.all()) {
      await expectSingleLineButton(button);
    }
    await expect(meaningCard.getByRole("button", { name: "この語釈を引用" })).toBeVisible();
    await expect(meaningCard.getByRole("button", { name: "お気に入り" })).toBeVisible();

    const cardBox = await meaningCard.boundingBox();
    const secondaryBox = await meaningCard.locator('[data-secondary-actions="1"]').boundingBox();
    expect(cardBox).not.toBeNull();
    expect(secondaryBox).not.toBeNull();
    expect(secondaryBox.x + secondaryBox.width).toBeLessThanOrEqual(cardBox.x + cardBox.width);

    await page.getByRole("button", { name: "用例", exact: true }).click();
    const exampleCard = page.locator(`#example-${fixture.exampleId}`);
    await expect(exampleCard).toBeVisible();
    await expect(exampleCard.locator('[data-reaction]')).toHaveCount(3);
    await expect(exampleCard.getByRole("button", { name: "この用例を引用" })).toBeVisible();
    await expect(exampleCard.getByRole("button", { name: "お気に入り" })).toBeVisible();
    await page.getByRole("button", { name: "語釈", exact: true }).click();
    await expect(meaningCard).toBeVisible();

    await page.setViewportSize({ width: 1024, height: 900 });
    await page.emulateMedia({ colorScheme: "dark" });
    const reactionsBox = await meaningCard.locator('[data-reaction-actions="1"]').boundingBox();
    const desktopSecondaryBox = await meaningCard.locator('[data-secondary-actions="1"]').boundingBox();
    expect(Math.abs(reactionsBox.y - desktopSecondaryBox.y)).toBeLessThan(8);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ colorScheme: "light" });
    await meaningCard.getByRole("button", { name: "この語釈を引用" }).click();
    const form = page.locator('[data-detail-post-actions="thread"]');
    await expect(form).toBeVisible();
    const selectedQuote = page.locator('[data-content-quote-card="語釈"]');
    await expect(selectedQuote).toBeVisible();
    await expect(selectedQuote.getByRole("button", { name: "引用を解除" })).toBeVisible();

    const postButton = form.getByRole("button", { name: "投稿する" });
    const cancelButton = form.getByRole("button", { name: "キャンセル" });
    const discardButton = form.getByRole("button", { name: "下書きを破棄" });
    await expectSingleLineButton(postButton);
    await expectSingleLineButton(cancelButton);
    await expectSingleLineButton(discardButton);
    const postBox = await postButton.boundingBox();
    const cancelBox = await cancelButton.boundingBox();
    const discardBox = await discardButton.boundingBox();
    expect(Math.abs(postBox.y - cancelBox.y)).toBeLessThan(2);
    expect(discardBox.y).toBeGreaterThan(postBox.y + postBox.height);

    await cancelButton.click();
    await expect(form).toHaveCount(0);

    await page.getByRole("button", { name: "語釈", exact: true }).click();
    await meaningCard.getByRole("button", { name: "この語釈を引用" }).click();
    await page.getByPlaceholder("タイトルを入力...").fill("破棄する引用スレッド");
    await form.getByRole("button", { name: "下書きを破棄" }).click();
    await page.getByRole("button", { name: "はい" }).click();
    await expect(form).toHaveCount(0);

    await page.getByRole("button", { name: "語釈", exact: true }).click();
    await meaningCard.getByRole("button", { name: "この語釈を引用" }).click();
    await page.getByPlaceholder("タイトルを入力...").fill("E2E引用スレッド");
    await form.getByRole("button", { name: "投稿する" }).click();
    await expect(page).toHaveURL(/\/threads\/\d+$/);
    await expect(page.getByText("E2E引用スレッド", { exact: true })).toBeVisible();
    const postedQuote = page.locator('[data-content-quote-card="語釈"]').first();
    await expect(postedQuote).toContainText("語釈から引用");
    await expect(postedQuote).toContainText("スマホ表示を確認するための語釈です");
    await expect(postedQuote).toContainText("出典 · E2E引用元ユーザー");
    await expect(postedQuote).toContainText("引用元を見る");
    await postedQuote.getByRole("button", { name: /引用元を見る/ }).click();
    await expect(page).toHaveURL(
      new RegExp(`/word/${fixture.wordId}/tag/${fixture.tagId}/meaning#meaning-${fixture.meaningId}$`),
    );
  } finally {
    if (fixture) await removeTestWordDetailFixture(fixture);
    await removeTestUser(userId);
  }
});
