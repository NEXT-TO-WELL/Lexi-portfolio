import { expect, test } from "@playwright/test";

test("health endpoint returns ok", async ({ page }) => {
  const response = await page.goto("/health");

  expect(response?.status()).toBe(200);
  await expect(page.locator("body")).toHaveText("ok");
});

test("service worker is served from the application root", async ({
  request,
}) => {
  const response = await request.get("/service-worker.js");

  expect(response.status()).toBe(200);
  expect(await response.text()).toContain('self.addEventListener("push"');
});

test("home page mounts the Scala.js application", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveTitle("Lexi SNS");
  await expect(
    page.getByRole("heading", { name: "今日のトレンド" }),
  ).toBeVisible();
  await expect(page.locator("#header-logotype")).toHaveAttribute(
    "src",
    "/assets/images/lexi-logotype.svg",
  );
  await expect(page.locator("#header-home-link")).toHaveAttribute(
    "href",
    "/home",
  );

  await page.locator("#header-home-link").click();
  await expect(page).toHaveURL(/\/home$/);
});

test("home header keeps the wordmark on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");

  await expect(page.locator("#header-logotype")).toBeVisible();
  await expect(page.locator("#header-symbol")).toHaveCount(0);

  const contentBox = await page.locator("#app-content").boundingBox();
  const navigationBox = await page.locator("#bottom-navigation").boundingBox();

  expect(contentBox).not.toBeNull();
  expect(navigationBox).not.toBeNull();
  expect(
    Math.abs(contentBox.y + contentBox.height - navigationBox.y),
  ).toBeLessThanOrEqual(1);

  const scrollContainer = page.locator("#app-content .overflow-y-auto").first();
  await scrollContainer.evaluate((element) => {
    element.lastElementChild.style.paddingBottom = "1200px";
  });
  await scrollContainer.hover();
  await page.mouse.wheel(0, 500);
  await expect
    .poll(() => scrollContainer.evaluate((element) => element.scrollTop))
    .toBeGreaterThan(0);
});

test("home page scrolls from outside the content column on desktop", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1200, height: 500 });
  await page.goto("/");
  await page.locator('[data-pull-to-refresh="true"]').evaluate((element) => {
    element.lastElementChild.style.paddingBottom = "1200px";
  });

  await page.mouse.move(20, 250);
  await page.mouse.wheel(0, 500);

  await expect
    .poll(() => page.evaluate(() => window.scrollY))
    .toBeGreaterThan(0);
});

test("sign-in page renders its form", async ({ page }) => {
  await page.goto("/signIn");

  await expect(page).toHaveTitle("Sign In - Lexi");
  await expect(page.locator("#auth-logotype")).toHaveAttribute(
    "src",
    "/assets/images/lexi-logotype.svg",
  );
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute(
    "href",
    "/assets/images/lexi-symbol.svg",
  );
  await expect(page.getByRole("heading", { name: "ログイン" })).toBeVisible();
  await expect(page.getByPlaceholder("ログインIDを入力")).toBeVisible();
  await expect(page.getByPlaceholder("パスワードを入力")).toBeVisible();
});
