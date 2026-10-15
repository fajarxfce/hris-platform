import { expect, type Page, test } from "@playwright/test";
import { carryExpiryId } from "../src/features/leave/di/leave-carry-expiry-fixture";
import { companyIds } from "./identity-api";
import { installLeaveCarryExpiryApi } from "./leave-carry-expiry-api";

const path = "/leave/carry-expiries",
  query = `company=${companyIds[0]}`;
const overview = (page: Page) => page.getByRole("region", { name: "Process details", exact: true });
const results = (page: Page) => page.getByRole("table", { name: "Employee results", exact: true });
async function verify(page: Page) {
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
}
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2027-04-01T00:02:00Z"));
});
test("expiry history opens retained employee results and their balance year", async ({ page }) => {
  const api = await installLeaveCarryExpiryApi(page);
  api.seed();
  await page.goto(`/leave/batches?${query}`);
  await page.getByRole("link", { name: "Carry expiry", exact: true }).click();
  await page.getByRole("button", { name: "View expiry: Annual leave · 2027", exact: true }).click();
  await expect(overview(page)).toContainText("2 / 3");
  await expect(overview(page)).toContainText("Mar 31, 2027");
  await expect(results(page)).toContainText("2.5");
  await expect(results(page)).toContainText("No change");
  await expect(
    page.getByRole("table", { name: "Process attempts", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  await page.screenshot({ path: "/tmp/hris-carry-expiry-ui/details-desktop.png", fullPage: true });
  await results(page)
    .getByRole("button", { name: "View balance: Example Employee", exact: true })
    .click();
  await expect.poll(() => api.batches.ledgerReads.length).toBe(1);
  expect(api.batches.ledgerReads[0]?.pathname).toMatch(/2027$/u);
  expect(api.identity.unhandled).toEqual([]);
});
test("lost recovery response retains the original command through MFA and later conflict", async ({
  page,
}) => {
  const api = await installLeaveCarryExpiryApi(page),
    item = api.seed();
  await page.goto(`${path}/${carryExpiryId}/resume?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Continue remaining work");
  api.loseCommand();
  await page.getByRole("button", { name: "Resume expiry", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  expect(api.committed).toBe(1);
  api.identity.expireMfa();
  api.rejectCommand("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await verify(page);
  api.rejectCommand("stale_version");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Expiry process resumed.");
  expect(api.committed).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  await page.getByRole("link", { name: "View expiry", exact: true }).click();
  await expect(overview(page)).toContainText("2 / 3");
  expect(item.attempts).toHaveLength(2);
  expect(item.results.items[0]?.jobId).not.toBe(item.job.id);
  api.complete(item);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(overview(page)).toContainText("3 / 3");
  await expect(results(page)).toContainText("No carry award");
  await expect(page.getByRole("link", { name: "Resume expiry", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  expect(api.identity.unhandled).toEqual([]);
});
test("expired assurance clears a review and reacquires it without submitting", async ({ page }) => {
  const api = await installLeaveCarryExpiryApi(page);
  api.seed();
  await page.goto(`${path}/${carryExpiryId}/resume?${query}`);
  await expect(page.getByLabel("Reason", { exact: true })).toBeVisible();
  api.identity.expireMfa();
  api.rejectRead("detail", "mfa_required");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await verify(page);
  await expect(page.getByLabel("Reason", { exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(0);
  expect(api.identity.unhandled).toEqual([]);
});
test("company replacement discards a late detail response and all prior cursors", async ({
  page,
}) => {
  const api = await installLeaveCarryExpiryApi(page);
  api.seed();
  await page.goto(`${path}/${carryExpiryId}?${query}`);
  await expect(results(page)).toBeVisible();
  const held = api.hold("detail");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("status")).toContainText("No carry expiry scheduled.");
    const delivered = api.delivered;
    held.release();
    await expect.poll(() => api.delivered).toBe(delivered + 1);
    await expect(results(page)).toHaveCount(0);
    await expect(page).toHaveURL(`${path}?company=${companyIds[1]}`);
    expect(
      api.reads.some((read) => read.kind === "detail" && read.url.pathname.includes(companyIds[1])),
    ).toBe(false);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});
test("narrow Indonesian review retains its draft and protects company navigation", async ({
  page,
}) => {
  const api = await installLeaveCarryExpiryApi(page);
  api.seed();
  await page.goto(`${path}/${carryExpiryId}/resume?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Pekerjaan tersisa sudah ditinjau");
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Pekerjaan tersisa sudah ditinjau",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: "/tmp/hris-carry-expiry-ui/review-mobile-id.png", fullPage: true });
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("dialog", { name: "Leave this page?", exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("No carry expiry scheduled.");
  await expect(page.getByRole("main")).not.toContainText("Pekerjaan tersisa");
  expect(api.identity.unhandled).toEqual([]);
});
