import { expect, test } from "@playwright/test";
import {
  createTestWordDetailFixture,
  removeTestUser,
  removeTestWordDetailFixture,
} from "./support/test-database.mjs";

const password = "e2e-password-123";

async function expectTabOrder(page) {
  const navigation = page.getByRole("navigation", { name: "主要ナビゲーション" });
  const tabs = navigation.locator("[data-tab-id]");

  await expect(tabs).toHaveCount(3);
  await expect(tabs).toHaveText(["ホーム", "投稿", "マイページ"]);
  await expect(tabs.nth(0)).toHaveAttribute("data-tab-id", "home");
  await expect(tabs.nth(1)).toHaveAttribute("data-tab-id", "post");
  await expect(tabs.nth(2)).toHaveAttribute("data-tab-id", "my");
  await expect(tabs.nth(1)).toHaveAttribute("data-action-emphasis", "true");

  const boxes = await Promise.all([tabs.nth(0).boundingBox(), tabs.nth(1).boundingBox(), tabs.nth(2).boundingBox()]);
  expect(boxes.every((box) => box !== null)).toBe(true);
  expect(boxes[0].x).toBeLessThan(boxes[1].x);
  expect(boxes[1].x).toBeLessThan(boxes[2].x);

  const navigationBox = await navigation.boundingBox();
  expect(boxes[1].y).toBeGreaterThanOrEqual(navigationBox.y);
  expect(boxes[1].y + boxes[1].height).toBeLessThanOrEqual(navigationBox.y + navigationBox.height);

  return navigation;
}

test("guest sees home, post, and my page in order with post disabled", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/home");

  const navigation = await expectTabOrder(page);
  const homeTab = navigation.locator('[data-tab-id="home"]');
  const postTab = navigation.locator('[data-tab-id="post"]');

  await expect(homeTab).toHaveClass(/text-blue-600/);
  await expect(postTab).toHaveAttribute("data-tab-state", "disabled");
  await expect(postTab).toHaveClass(/opacity-60/);
  await expect(postTab).toHaveAttribute("title", "ログインすると投稿できます");
  const [homeBackground, postBackground] = await Promise.all([
    homeTab.evaluate((element) => getComputedStyle(element).backgroundColor),
    postTab.evaluate((element) => getComputedStyle(element).backgroundColor),
  ]);
  expect(postBackground).not.toBe(homeBackground);

  await postTab.focus();
  await expect(postTab).toBeFocused();
  await expect.poll(() => postTab.evaluate((element) => getComputedStyle(element).boxShadow)).not.toBe("none");
  await postTab.click();
  await expect(page).toHaveURL(/\/home$/);

  await page.setViewportSize({ width: 1280, height: 900 });
  const desktopNavigation = await expectTabOrder(page);
  const navigationBox = await desktopNavigation.boundingBox();
  expect(navigationBox).not.toBeNull();
  expect(navigationBox.width).toBeLessThanOrEqual(768);
});

test("signed-in user can switch tabs while home history is preserved", async ({ page }, testInfo) => {
  const suffix = `${Date.now()}-${testInfo.workerIndex}`;
  const userId = `e2e-tab-order-${suffix}`;
  let fixture;

  try {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/signIn");
    await page.getByRole("button", { name: "新規登録" }).click();
    await page.getByPlaceholder("例: Lexiユーザー").fill("E2E Tab Order User");
    await page.getByPlaceholder("ログインIDを入力").fill(userId);
    await page.getByPlaceholder("パスワードを入力", { exact: true }).fill(password);
    await page.getByPlaceholder("もう一度パスワードを入力").fill(password);
    await page.getByRole("button", { name: "アカウント作成" }).click();
    await page.getByRole("button", { name: "確認" }).click();

    fixture = await createTestWordDetailFixture(suffix);
    const detailPath = `/word/${fixture.wordId}/tag/${fixture.tagId}/meaning`;
    await page.goto(detailPath);

    const navigation = await expectTabOrder(page);
    const homeTab = navigation.locator('[data-tab-id="home"]');
    const postTab = navigation.locator('[data-tab-id="post"]');

    await expect(homeTab).toHaveClass(/text-blue-600/);
    await expect(postTab).toHaveAttribute("data-tab-state", "default");
    await postTab.click();
    await expect(page).toHaveURL(/\/post$/);
    await expect(postTab).toHaveAttribute("data-tab-state", "active");
    const selectedPostBackground = await postTab.evaluate((element) => getComputedStyle(element).backgroundColor);
    const selectedHomeBackground = await homeTab.evaluate((element) => getComputedStyle(element).backgroundColor);
    expect(selectedPostBackground).not.toBe(selectedHomeBackground);

    await page.emulateMedia({ colorScheme: "dark" });
    const darkPostBackground = await postTab.evaluate((element) => getComputedStyle(element).backgroundColor);
    const darkHomeBackground = await homeTab.evaluate((element) => getComputedStyle(element).backgroundColor);
    expect(darkPostBackground).not.toBe(darkHomeBackground);

    await page.setViewportSize({ width: 1280, height: 900 });
    const desktopNavigation = await expectTabOrder(page);
    const desktopPostTab = desktopNavigation.locator('[data-tab-id="post"]');
    await expect(desktopPostTab).toHaveAttribute("data-tab-state", "active");

    await desktopNavigation.locator('[data-tab-id="home"]').click();
    await expect(page).toHaveURL(new RegExp(`${detailPath}$`));
    await expect(homeTab).toHaveClass(/text-blue-600/);
    await expect(postTab).toHaveAttribute("data-tab-state", "default");

    await desktopNavigation.locator('[data-tab-id="home"]').click();
    await expect(page).toHaveURL(/\/home$/);
    await page.locator('[data-pull-to-refresh="true"]').evaluate((element) => {
      element.lastElementChild.style.paddingBottom = "1600px";
    });
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);

    await desktopPostTab.click();
    await expect(page).toHaveURL(/\/post$/);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);

    for (const position of [
      { x: 640, y: 400 },
      { x: 20, y: 400 },
      { x: 640, y: 870 },
    ]) {
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.mouse.move(position.x, position.y);
      await page.mouse.wheel(0, 500);
      await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
    }
  } finally {
    if (fixture) await removeTestWordDetailFixture(fixture);
    await removeTestUser(userId);
  }
});
