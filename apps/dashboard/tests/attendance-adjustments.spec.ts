import { expect, type Page, test } from "@playwright/test";
import {
  adjustmentDate,
  adjustmentId,
} from "../src/features/workforce/di/attendance-adjustment-fixture";
import {
  adjustmentEditorPath,
  adjustmentHistoryPath,
  adjustmentResultPath,
  installAttendanceAdjustmentApi,
} from "./attendance-adjustment-api";
import { companyIds } from "./identity-api";

async function prepare(page: Page) {
  await page.goto(adjustmentEditorPath);
  await expect(page.getByRole("table", { name: "Current attendance", exact: true })).toBeVisible();
  await page.getByRole("checkbox", { name: "Record an absence", exact: true }).check();
  await page
    .getByRole("textbox", { name: "Reason", exact: true })
    .fill("Reviewed absence after closing");
}
test.use({ timezoneId: "Pacific/Honolulu" });
test("a closed day requires explicit pending decisions and preserves the original closing", async ({
  page,
}) => {
  const api = await installAttendanceAdjustmentApi(page);
  await page.goto(adjustmentResultPath);
  await page.getByRole("button", { name: `Review day: ${adjustmentDate}`, exact: true }).click();
  await expect(page).toHaveURL(adjustmentEditorPath);
  const decision = page.getByRole("combobox", { name: /^Decision · Clock-in/ });
  await expect(decision).toHaveValue("");
  await page.getByRole("checkbox", { name: "Record an absence", exact: true }).check();
  await page
    .getByRole("textbox", { name: "Reason", exact: true })
    .fill("Reviewed absence after closing");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  expect(api.writes).toHaveLength(0);
  await decision.selectOption("REJECT");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Attendance adjustment recorded." }),
  ).toBeVisible();
  expect(api.writes[0]?.body).toMatchObject({
    clockIn: null,
    clockOut: null,
    decisions: [{ expectedVersion: 0, decision: "REJECT" }],
  });
  await page.getByRole("link", { name: "View recorded revision", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Retained monthly attendance", exact: true }),
  ).toContainText("Recorded absence");
  await page.getByRole("link", { name: "Original closing", exact: true }).click();
  expect(api.original.days[4]?.fact).toBe("UNRECORDED");
  await expect(page.getByRole("table", { name: "Daily results", exact: true })).toBeVisible();
  expect(api.identity.unhandled).toEqual([]);
});
test("a lost response retains the original operation and payload through a later conflict", async ({
  page,
}) => {
  const api = await installAttendanceAdjustmentApi(page);
  api.loseNext();
  await prepare(page);
  await page.getByRole("combobox", { name: /^Decision · Clock-in/ }).selectOption("REJECT");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original adjustment", exact: true });
  await expect(retry).toBeVisible();
  api.changeReview();
  api.rejectNext("attendance_adjustment_changed");
  await retry.click();
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(
    page.getByRole("link", { name: "View recorded revision", exact: true }),
  ).toHaveAttribute("href", new RegExp(adjustmentId));
  expect(api.writes).toHaveLength(3);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[2]).toEqual(api.writes[0]);
});
test("a definite source conflict protects input and requires reviewed replacement", async ({
  page,
}) => {
  const api = await installAttendanceAdjustmentApi(page);
  await prepare(page);
  await page.getByRole("combobox", { name: /^Decision · Clock-in/ }).selectOption("ACCEPT");
  api.changeReview();
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("button", { name: "Record adjustment", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("combobox", { name: /^Decision · Clock-in/ })).toHaveValue("");
  await page.getByRole("checkbox", { name: "Record an absence", exact: true }).check();
  await page.getByRole("textbox", { name: "Reason", exact: true }).fill("New review");
  await page.getByRole("combobox", { name: /^Decision · Clock-in/ }).selectOption("REJECT");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "View recorded revision", exact: true }),
  ).toBeVisible();
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.writes[1]?.body.reviewRevision).not.toBe(api.writes[0]?.body.reviewRevision);
});
test("history uses bounded pages and exact revisions retain their return page", async ({
  page,
}) => {
  const api = await installAttendanceAdjustmentApi(page);
  api.populateHistory(26);
  await page.goto(adjustmentHistoryPath);
  const table = page.getByRole("table", { name: "Adjustment history", exact: true });
  await expect(table.getByRole("row")).toHaveCount(26);
  await page.getByRole("button", { name: "Earlier revisions", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await table
    .getByRole("button", { name: `View revision: 1 · ${adjustmentDate}`, exact: true })
    .click();
  await expect(
    page.getByRole("table", { name: "Retained monthly attendance", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Back to history", exact: true }).click();
  await expect(page).toHaveURL(`${adjustmentHistoryPath}&before=2`);
  await expect(table.getByRole("row")).toHaveCount(2);
  expect(api.identity.unhandled).toEqual([]);
});
test("read-only hints and old API metadata do not offer correction commands", async ({ page }) => {
  await installAttendanceAdjustmentApi(page, { supported: false, canCorrect: false });
  await page.goto(adjustmentResultPath);
  await expect(page.getByRole("table", { name: "Daily results", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Adjustment history", exact: true })).toHaveCount(0);
  await page.goto(adjustmentEditorPath);
  await expect(
    page.getByText("Your current access allows review only.", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Record adjustment", exact: true })).toHaveCount(0);
});
test("company replacement discards a pending private review", async ({ page }) => {
  const api = await installAttendanceAdjustmentApi(page),
    held = api.holdRead();
  await page.goto(adjustmentEditorPath);
  await held.started;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  held.release();
  await expect(page.getByRole("table", { name: "Current attendance", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Record adjustment", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("narrow dark Indonesian review retains input with doubled text", async ({ page }) => {
  const api = await installAttendanceAdjustmentApi(page);
  await prepare(page);
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  const main = page.getByRole("main");
  await main.evaluate((node) => {
    const values = [...node.querySelectorAll<HTMLElement>("*")].map((element) => ({
      element,
      size: Number.parseFloat(getComputedStyle(element).fontSize),
      height: Number.parseFloat(getComputedStyle(element).lineHeight),
    }));
    for (const { element, size, height } of values) {
      if (Number.isFinite(size)) element.style.fontSize = `${size * 2}px`;
      if (Number.isFinite(height)) element.style.lineHeight = `${height * 2}px`;
    }
  });
  await expect(page.getByRole("textbox", { name: "Alasan", exact: true })).toHaveValue(
    "Reviewed absence after closing",
  );
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.getByRole("combobox", { name: /^Keputusan · Masuk/ }).selectOption("REJECT");
  await page.getByRole("textbox", { name: "Alasan", exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({
    path: "../../.work/dashboard-attendance-adjustment-narrow.png",
    animations: "disabled",
  });
  expect(api.reads).toHaveLength(reads);
});
