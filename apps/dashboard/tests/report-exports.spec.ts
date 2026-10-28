import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installReportExportsApi, reportExportId } from "./report-exports-api";

const directory = `/reports/exports?company=${companyIds[0]}`,
  detail = `/reports/exports/${reportExportId}?company=${companyIds[0]}`;
const creating = `/reports/exports/new?company=${companyIds[0]}&kind=HEADCOUNT&asOf=2026-10-01&companies=${companyIds[0]}`;

test("applied filters create one export, progress to ready and download exact CSV", async ({
  page,
}) => {
  const api = await installReportExportsApi(page, { empty: true });
  await page.goto(`/reports/headcount?asOf=2026-10-01&companies=${companyIds[0]}`);
  await expect(
    page.getByRole("link", { name: "Export applied filters", exact: true }),
  ).toBeVisible();
  await page.getByLabel("As of date", { exact: true }).fill("2026-10-20");
  await page.getByRole("link", { name: "Export applied filters", exact: true }).click();
  const navigation = page.getByRole("navigation");
  await expect(
    navigation.getByRole("link", { name: "Report exports", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await expect(
    navigation.getByRole("link", { name: "Report catalogue", exact: true }),
  ).not.toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("region", { name: "Applied filters", exact: true })).toContainText(
    "2026-10-01",
  );
  await expect(page.getByLabel("Contents", { exact: true }).locator("option")).toHaveCount(1);
  await page.getByRole("button", { name: "Create CSV export", exact: true }).click();
  await page.getByRole("link", { name: "View export", exact: true }).click();
  await expect(
    navigation.getByRole("link", { name: "Report exports", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("region", { name: "Status", exact: true })).toContainText("Queued");
  api.ready();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  const downloadEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download CSV", exact: true }).click();
  const download = await downloadEvent;
  expect(download.suggestedFilename()).toBe(`report-headcount-${reportExportId}.csv`);
  expect(await readFile(await download.path())).toEqual(api.csv);
  await expect(page.getByRole("status")).toContainText("The file has been sent to your browser.");
  await page.getByRole("link", { name: "Back to history", exact: true }).click();
  await expect(
    navigation.getByRole("link", { name: "Report exports", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("table", { name: "Export history", exact: true })).toContainText(
    "Ready",
  );
  expect(api.exportWrites).toHaveLength(1);
  expect(api.exportWrites[0]?.body).toMatchObject({
    kind: "HEADCOUNT",
    parameters: { asOf: "2026-10-01" },
    dataset: "SUMMARY",
  });
  expect(api.identity.unhandled).toEqual([]);
  await page.screenshot({
    path: "../../.work/report-export-history-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("lost creation response survives MFA and repeated rejection with the original command", async ({
  page,
}) => {
  const api = await installReportExportsApi(page, { empty: true });
  await page.goto(creating);
  api.dropNext();
  await page.getByRole("button", { name: "Create CSV export", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Create CSV export", exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("link", { name: "View export", exact: true })).toBeVisible();
  expect(api.exportWrites).toHaveLength(4);
  for (const write of api.exportWrites) expect(write).toEqual(api.exportWrites[0]);
  expect(api.identity.unhandled).toEqual([]);
});

test("cancellation and recovery require review and retain versions through lost responses", async ({
  page,
}) => {
  const api = await installReportExportsApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "Cancel export", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Cancel", exact: true }).click();
  expect(api.exportWrites).toHaveLength(0);
  await page.getByRole("button", { name: "Cancel export", exact: true }).click();
  api.dropNext();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Cancel export", exact: true })
    .click();
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("region", { name: "Status", exact: true })).toContainText("Stopped");
  await expect(page.getByRole("button", { name: "Resume export", exact: true })).toHaveCount(0);
  api.patch({ jobStatus: "CANCELLED" });
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "Resume export", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Resume export", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Status", exact: true })).toContainText("Queued");
  expect(api.exportWrites.map((item) => item.body.expectedVersion)).toEqual([0, 0, 1]);
  expect(api.exportWrites[0]).toEqual(api.exportWrites[1]);
  expect(api.identity.unhandled).toEqual([]);
});

test("current source loss permits owned status and cancellation but hides content actions", async ({
  page,
}) => {
  const api = await installReportExportsApi(page, { readerOnly: true });
  await page.goto(detail);
  await expect(page.getByRole("button", { name: "Cancel export", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Cancel export", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Cancel export", exact: true })
    .click();
  api.patch({ jobStatus: "CANCELLED" });
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("button", { name: "Resume export", exact: true })).toHaveCount(0);
  api.ready();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});

test("history replaces bounded pages and isolates company switches from delayed reads", async ({
  page,
}) => {
  const api = await installReportExportsApi(page, { count: 21 });
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Export history", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Older exports", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await page.getByRole("button", { name: "Newest exports", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(21);
  const held = api.holdExportRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(table.getByRole("row")).toHaveCount(21);
    held.release();
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
    await expect(table.getByRole("button", { name: /b1000000/u })).toHaveCount(0);
  } finally {
    held.release();
  }
  expect(api.exportReads.some((url) => url.searchParams.has("beforeAt"))).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("expired files and denied downloads clear the old detail before another action", async ({
  page,
}) => {
  const api = await installReportExportsApi(page);
  api.ready();
  await page.goto(detail);
  api.failContent("report_export_expired");
  await page.getByRole("button", { name: "Download CSV", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This export has expired.");
  await expect(page.getByRole("region", { name: "Status", exact: true })).toHaveCount(0);
  api.patch({ expired: true });
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Status", exact: true })).toContainText("Expired");
  await expect(page.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});

test("details choice and Indonesian dark narrow review remain readable", async ({ page }) => {
  const api = await installReportExportsApi(page, { empty: true });
  await page.goto(
    `/reports/exports/new?company=${companyIds[0]}&kind=LEAVE&from=2026-10-01&until=2026-10-20`,
  );
  await page.getByLabel("Contents", { exact: true }).selectOption("DETAIL");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Isi", { exact: true })).toHaveValue("DETAIL");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/report-export-review-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Buat ekspor CSV", exact: true }).click();
  await expect(page.getByRole("link", { name: "Lihat ekspor", exact: true })).toBeVisible();
  expect(api.exportWrites[0]?.body).toMatchObject({ kind: "LEAVE", dataset: "DETAIL" });
  expect(api.identity.unhandled).toEqual([]);
});

test("invalid selections never submit and history failures discard stale rows", async ({
  page,
}) => {
  const api = await installReportExportsApi(page);
  await page.goto(`${creating}&asOf=2026-10-02`);
  await expect(page.getByRole("alert")).toContainText("Check the report dates");
  expect(api.exportWrites).toHaveLength(0);
  await page.goto(directory);
  await expect(page.getByRole("table", { name: "Export history", exact: true })).toBeVisible();
  api.failRead("access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Export history", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});
