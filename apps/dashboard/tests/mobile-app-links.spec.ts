import { expect, test } from "@playwright/test";

const item =
  "accounts/11111111-1111-4111-8111-111111111111/companies/22222222-2222-4222-8222-222222222222/inbox/33333333-3333-4333-8333-333333333333";

test("public mobile handoff preserves the destination without session or business requests", async ({
  page,
}) => {
  const requests: string[] = [];
  await page.route("**/api/**", async (route) => {
    requests.push(route.request().method());
    await route.fulfill({ status: 500 });
  });
  await page.goto(`/app/dev/${item}`);
  await expect(page.getByRole("heading", { level: 1, name: "Open in HRIS Mobile" })).toBeVisible();
  const open = page.getByRole("link", { name: "Open HRIS Mobile", exact: true });
  await expect(open).toHaveAttribute("href", `hrismobile-dev://app/${item}`);
  await expect(open).toHaveAttribute("referrerpolicy", "no-referrer");
  await expect(page.locator("body")).not.toContainText("11111111-1111-4111-8111-111111111111");
  await expect(page).toHaveURL(new RegExp(`/app/dev/${item}$`, "u"));
  await page.evaluate(() => {
    window.dispatchEvent(new Event("online"));
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(open).toBeVisible();
  expect(requests).toEqual([]);
});

test("invalid mobile links stay public and never offer a native action", async ({ page }) => {
  const requests: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.startsWith("/api/")) requests.push(request.method());
  });
  for (const path of [
    "/app/dev/inbox?read=true",
    "/app/dev/inbox#token=private",
    "/app/dev/%69nbox",
    "/app/qa/inbox",
    "/app/dev/accounts/invalid",
  ]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name: "Link unavailable" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Open HRIS Mobile", exact: true })).toHaveCount(0);
    await expect(page.locator("body")).not.toContainText("private");
  }
  expect(requests).toEqual([]);
});

test("narrow handoff uses the selected environment and existing language and theme controls", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await page.goto("/app/staging/inbox");
  await expect(page.getByRole("link", { name: "Open HRIS Mobile", exact: true })).toHaveAttribute(
    "href",
    "hrismobile-staging://app/inbox",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole("link", { name: "Open HRIS Mobile", exact: true }).focus();
  await expect(page.getByRole("link", { name: "Open HRIS Mobile", exact: true })).toBeFocused();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("heading", { level: 1, name: "Buka di HRIS Mobile" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Buka HRIS Mobile", exact: true })).toHaveAttribute(
    "href",
    "hrismobile-staging://app/inbox",
  );
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tema", exact: true })).toHaveText("Terang");
  await page.screenshot({ path: "../../.work/mobile-link-landing-id-dark.png", fullPage: true });
});
