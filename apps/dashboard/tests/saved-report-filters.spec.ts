import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import {
  installSavedFiltersApi,
  savedFilterId,
  unavailableCompany,
} from "./saved-report-filters-api";

const directory = `/reports/saved-filters?company=${companyIds[0]}`;
const editor = `/reports/saved-filters/${savedFilterId}?company=${companyIds[0]}`;
test("company-pinned headcount selections reset before another company's report is read", async ({
  page,
}) => {
  const api = await installSavedFiltersApi(page);
  await page.goto(
    `/reports/headcount?company=${companyIds[0]}&asOf=2026-10-01&companies=${companyIds[0]}`,
  );
  await expect(page.getByRole("link", { name: "Save applied filter", exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
  await expect(page.getByRole("link", { name: "Save applied filter", exact: true })).toBeVisible();
  expect(api.reportReads.at(-1)?.searchParams.get("companies")).toBe(companyIds[1]);
  expect(api.identity.unhandled).toEqual([]);
});
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Filter name", { exact: true })).toHaveValue("October headcount");
}

test("applied report selections create a private filter, reopen, edit and delete its observed version", async ({
  page,
}) => {
  const api = await installSavedFiltersApi(page, { empty: true });
  await page.goto(`/reports/headcount?asOf=2026-10-01&companies=${companyIds[0]}`);
  await expect(page.getByRole("link", { name: "Save applied filter", exact: true })).toBeVisible();
  await page.getByLabel("As of date", { exact: true }).fill("2026-10-20");
  await page.getByRole("link", { name: "Save applied filter", exact: true }).click();
  await expect(page.getByLabel("As of", { exact: true })).toHaveValue("2026-10-01");
  await page.getByLabel("Filter name", { exact: true }).fill("Monthly review");
  await page.getByRole("button", { name: "Save filter", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Filter saved.");
  await page.getByRole("link", { name: "Back to saved filters", exact: true }).click();
  await page.getByRole("button", { name: "Review: Monthly review", exact: true }).click();
  await page.getByRole("link", { name: "Open saved selection", exact: true }).click();
  await expect(page).toHaveURL(/asOf=2026-10-01/u);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toBeVisible();
  await page.goBack();
  await page.getByLabel("Filter name", { exact: true }).fill("Reviewed selection");
  await page.getByRole("button", { name: "Save filter", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Filter saved.");
  await page.getByRole("link", { name: "Back to saved filters", exact: true }).click();
  await page.getByRole("button", { name: "Review: Reviewed selection", exact: true }).click();
  await page.getByRole("button", { name: "Delete filter", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Delete filter", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Filter deleted.");
  await page.getByRole("link", { name: "Back to saved filters", exact: true }).click();
  await expect(
    page.getByText("No saved filters in this company yet.", { exact: true }),
  ).toBeVisible();
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([null, 0, 1]);
  expect(api.writes.map((write) => write.method)).toEqual(["PUT", "PUT", "POST"]);
  expect(api.identity.unhandled).toEqual([]);
});

test("uncertain saves retain the exact original command through MFA and later conflicts", async ({
  page,
}) => {
  const api = await installSavedFiltersApi(page);
  await openEditor(page);
  await page.getByLabel("Filter name", { exact: true }).fill("Retained command");
  api.dropNext();
  await page.getByRole("button", { name: "Save filter", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await expect(page.getByLabel("Filter name", { exact: true })).toBeDisabled();
  const reads = api.reads.length;
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Recover original command", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByLabel("Filter name", { exact: true })).toHaveValue("Retained command");
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Recover original command", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Reload current filter", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Recover original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Filter saved.");
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.reads).toHaveLength(reads);
  expect(api.identity.unhandled).toEqual([]);
});

test("deletion requires confirmation and recovers its receipt after the definition is gone", async ({
  page,
}) => {
  const api = await installSavedFiltersApi(page);
  await openEditor(page);
  await page.getByRole("button", { name: "Delete filter", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Cancel", exact: true }).click();
  expect(api.writes).toHaveLength(0);
  api.dropNext();
  await page.getByRole("button", { name: "Delete filter", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Delete filter", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  expect(api.records.get(companyIds[0])).toEqual([]);
  await page.getByRole("button", { name: "Recover original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Filter deleted.");
  expect(api.writes[1]).toEqual(api.writes[0]);
});

test("retained unavailable company selections survive name edits and source loss still permits deletion", async ({
  page,
  context,
}) => {
  const api = await installSavedFiltersApi(page, { unknownSelection: true });
  await openEditor(page);
  await page.getByLabel("Filter name", { exact: true }).fill("Keep all selections");
  await page.getByRole("button", { name: "Save filter", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Filter saved.");
  expect(api.writes[0]?.body).toMatchObject({ parameters: { companies: [unavailableCompany] } });
  const reader = await context.newPage();
  try {
    const restricted = await installSavedFiltersApi(reader, {
      readerOnly: true,
      unknownSelection: true,
    });
    await openEditor(reader);
    await expect(
      reader.getByRole("link", { name: "Open saved selection", exact: true }),
    ).toHaveCount(0);
    await expect(reader.getByRole("button", { name: "Save filter", exact: true })).toBeDisabled();
    await reader.getByRole("button", { name: "Delete filter", exact: true }).click();
    await reader
      .getByRole("dialog")
      .getByRole("button", { name: "Delete filter", exact: true })
      .click();
    await expect(reader.getByRole("status")).toContainText("Filter deleted.");
    expect(restricted.reportReads).toHaveLength(0);
    expect(restricted.identity.unhandled).toEqual([]);
  } finally {
    await reader.close();
  }
});

test("stale versions require a protected reload before another save", async ({ page }) => {
  const api = await installSavedFiltersApi(page);
  await openEditor(page);
  await page.getByLabel("Filter name", { exact: true }).fill("My edit");
  api.advance();
  await page.getByRole("button", { name: "Save filter", exact: true }).click();
  await page.getByRole("button", { name: "Reload current filter", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Filter name", { exact: true })).toHaveValue("Changed elsewhere");
  await page.getByLabel("Filter name", { exact: true }).fill("Reviewed edit");
  await page.getByRole("button", { name: "Save filter", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Filter saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([2, 3]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("pending saves protect departures and company replacement cancels obsolete directory data", async ({
  page,
}) => {
  const api = await installSavedFiltersApi(page);
  await openEditor(page);
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Save filter", exact: true }).click();
    await held.entered;
    await expect(page.getByLabel("Filter name", { exact: true })).toBeDisabled();
    await page.getByRole("link", { name: "Back to saved filters", exact: true }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Stay on this page", exact: true })
      .click();
    held.release();
    await expect(page.getByRole("status")).toContainText("Filter saved.");
  } finally {
    held.release();
  }
  await page.getByRole("link", { name: "Back to saved filters", exact: true }).click();
  const heldRead = api.holdRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await heldRead.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(
      page.getByRole("button", { name: "Review: Other company filter", exact: true }),
    ).toBeVisible();
    heldRead.release();
    await expect(
      page.getByRole("button", { name: "Review: October headcount", exact: true }),
    ).toHaveCount(0);
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
  } finally {
    heldRead.release();
  }
});

test("fifty-filter limits and Indonesian dark narrow editor remain readable", async ({ page }) => {
  const api = await installSavedFiltersApi(page, { count: 50 });
  await page.goto(directory);
  await expect(
    page.getByRole("table", { name: "Saved filters", exact: true }).getByRole("row"),
  ).toHaveCount(51);
  await expect(page.getByRole("link", { name: "Create filter", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Review: October headcount", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Nama filter", { exact: true })).toHaveValue("October headcount");
  await expect(page.getByRole("button", { name: "Simpan filter", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/saved-filter-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.identity.unhandled).toEqual([]);
});
