import { expect, test } from "@playwright/test";
import {
  createTestWordDetailFixture,
  removeTestWordDetailFixture,
} from "./support/test-database.mjs";

function countRequests(page, pathname) {
  let count = 0;
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === pathname) count += 1;
  });
  return () => count;
}

async function dispatchTouch(page, selector, type, clientY) {
  await page.locator(selector).evaluate(
    (element, event) => {
      const touch = new Touch({
        identifier: 1,
        target: element,
        clientX: 120,
        clientY: event.clientY,
      });
      const activeTouches = event.type === "touchend" ? [] : [touch];
      element.dispatchEvent(
        new TouchEvent(event.type, {
          bubbles: true,
          cancelable: true,
          changedTouches: [touch],
          targetTouches: activeTouches,
          touches: activeTouches,
        }),
      );
    },
    { type, clientY },
  );
}

async function pull(page, startY, endY) {
  const selector = '[data-pull-to-refresh="true"]';
  await dispatchTouch(page, selector, "touchstart", startY);
  await dispatchTouch(page, selector, "touchmove", endY);
}

test("mobile pull-to-refresh respects its threshold, scroll position, and refresh lock", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const trendRequests = countRequests(page, "/trends");
  await page.goto("/home");
  await expect(
    page.getByRole("heading", { name: "今日のトレンド" }),
  ).toBeVisible();
  await expect.poll(trendRequests).toBe(1);
  await expect(page.locator("#desktop-refresh-button")).toBeHidden();

  const container = page.locator('[data-pull-to-refresh="true"]');
  const indicator = container.locator("[data-pull-state]");
  await page.waitForTimeout(3100);

  await pull(page, 100, 180);
  await expect(indicator).toHaveAttribute("data-pull-state", "pulling");
  const headerBoxWhilePulling = await page.locator("header").boundingBox();
  const indicatorBoxWhilePulling = await indicator.boundingBox();
  expect(indicatorBoxWhilePulling.y).toBeGreaterThanOrEqual(
    headerBoxWhilePulling.y + headerBoxWhilePulling.height,
  );
  await dispatchTouch(page, '[data-pull-to-refresh="true"]', "touchend", 180);
  await expect(indicator).toHaveAttribute("data-pull-state", "idle");
  expect(trendRequests()).toBe(1);

  await pull(page, 100, 260);
  await expect(indicator).toHaveAttribute("data-pull-state", "ready");
  await dispatchTouch(page, '[data-pull-to-refresh="true"]', "touchmove", 160);
  await expect(indicator).toHaveAttribute("data-pull-state", "pulling");
  await dispatchTouch(page, '[data-pull-to-refresh="true"]', "touchend", 160);
  await expect(indicator).toHaveAttribute("data-pull-state", "idle");
  expect(trendRequests()).toBe(1);

  await container.evaluate((element) => {
    element.lastElementChild.style.paddingBottom = "1200px";
    element.scrollTop = 100;
  });
  await expect
    .poll(() => container.evaluate((element) => element.scrollTop))
    .toBeGreaterThan(0);
  await pull(page, 100, 280);
  await dispatchTouch(page, '[data-pull-to-refresh="true"]', "touchend", 280);
  await expect(indicator).toHaveAttribute("data-pull-state", "idle");
  expect(trendRequests()).toBe(1);

  await container.evaluate((element) => {
    element.scrollTop = 0;
  });
  await pull(page, 100, 260);
  await expect(indicator).toHaveAttribute("data-pull-state", "ready");
  await expect(indicator).toContainText("離して更新");
  const headerBox = await page.locator("header").boundingBox();
  const indicatorBox = await indicator.boundingBox();
  expect(indicatorBox.y).toBeGreaterThanOrEqual(headerBox.y + headerBox.height);
  await dispatchTouch(page, '[data-pull-to-refresh="true"]', "touchend", 260);
  await expect(indicator).toHaveAttribute("data-pull-state", "refreshing");
  await expect.poll(trendRequests).toBe(2);

  await pull(page, 100, 280);
  await dispatchTouch(page, '[data-pull-to-refresh="true"]', "touchend", 280);
  await page.waitForTimeout(100);
  expect(trendRequests()).toBe(2);
  await expect(indicator).toHaveAttribute("data-pull-state", "idle");
});

test("desktop refresh remains available and refreshes the active word detail tab", async ({
  page,
}, testInfo) => {
  const fixture = await createTestWordDetailFixture(
    `${Date.now()}-${testInfo.workerIndex}`,
  );
  const headerPath = `/words/${fixture.wordId}/${fixture.tagId}/details`;
  const meaningPath = `/words/${fixture.wordId}/${fixture.tagId}/meanings/paginated`;
  const examplePath = `/words/${fixture.wordId}/${fixture.tagId}/examples/paginated`;
  const headerRequests = countRequests(page, headerPath);
  const meaningRequests = countRequests(page, meaningPath);
  const exampleRequests = countRequests(page, examplePath);

  try {
    await page.setViewportSize({ width: 1280, height: 900 });
    const trendWordName = "E2E更新遷移テスト";
    await page.route("**/trends", async (route) => {
      await route.fulfill({
        json: {
          buzz: [],
          hot: [],
          news: [
            {
              count: 0,
              rank: 1,
              tagId: fixture.tagId,
              tagName: "E2E",
              trendType: "NEW",
              wordId: fixture.wordId,
              wordName: trendWordName,
            },
          ],
          random: [],
        },
      });
    });
    await page.goto("/home");
    await page.getByText(trendWordName, { exact: true }).click();
    await expect(page).toHaveURL(
      new RegExp(`/word/${fixture.wordId}/tag/${fixture.tagId}/meaning$`),
    );
    await expect.poll(headerRequests).toBe(1);
    await expect.poll(meaningRequests).toBe(1);

    const detailScrollContainer = page.locator('[data-pull-to-refresh="true"]');
    await detailScrollContainer.locator(":scope > div:last-child").evaluate((element) => {
      element.style.paddingBottom = "1400px";
    });
    await page.getByText("スマホ表示を確認するための語釈です").hover();
    await page.mouse.wheel(0, 500);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
    await page.evaluate(() => window.scrollTo(0, 0));

    const refreshButton = page.locator("#desktop-refresh-button");
    await expect(refreshButton).toBeVisible();
    await refreshButton.click();
    await expect.poll(headerRequests).toBe(2);
    await expect.poll(meaningRequests).toBe(2);

    await page.getByRole("button", { name: "用例", exact: true }).click();
    await expect.poll(exampleRequests).toBe(1);
    await expect(refreshButton).toBeEnabled();
    await refreshButton.click();
    await expect.poll(headerRequests).toBe(3);
    await expect.poll(exampleRequests).toBe(2);
    expect(meaningRequests()).toBe(2);
  } finally {
    await removeTestWordDetailFixture(fixture);
  }
});
