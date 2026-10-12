import { expect, test } from "@playwright/test";
import { employeeId } from "./attendance-api";
import { installAttendanceHistoryApi } from "./attendance-history-api";
import { companyIds } from "./identity-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const day = `/workforce/attendance/${employeeId}/days/2026-10-12`,
  history = `${day}/history`;
const scope = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;

test("attendance history pages retain the work date, filter range and immutable correction details", async ({
  page,
}) => {
  const api = await installAttendanceHistoryApi(page);
  await page.goto(`${day}?${scope}`);
  await page.getByRole("link", { name: "Correction history", exact: true }).click();
  await expect(page).toHaveURL(`${history}?${scope}`);
  const table = page.getByRole("table", { name: "Corrections", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(
    page.getByRole("region", { name: "Employee and work date", exact: true }),
  ).toContainText("North Employee");
  const first = page.getByRole("button", { name: "View correction: 52", exact: true });
  await first.click();
  const panel = page.getByRole("dialog", { name: "Correction details", exact: true });
  await expect(panel).toContainText("Attendance correction 52");
  await expect(panel.getByText("Oct 12, 2026, 10:15 PM", { exact: true })).toBeVisible();
  await expect(panel.getByText("Oct 13, 2026, 6:00 AM", { exact: true })).toBeVisible();
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await expect(first).toBeFocused();
  expect(api.reads).toHaveLength(1);
  await page.getByRole("button", { name: "Older corrections", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(page).toHaveURL(`${history}?${scope}&after=3`);
  await expect(page.getByRole("button", { name: "Older corrections", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "View correction: 0", exact: true }).click();
  await expect(panel).toContainText("Verified absence");
  await expect(panel.getByText("Absence", { exact: true })).toBeVisible();
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(page).toHaveURL(`${history}?${scope}`);
  await page.getByRole("link", { name: "Back to attendance day", exact: true }).click();
  await expect(page).toHaveURL(`${day}?${scope}`);
  expect(api.reads.map((url) => url.searchParams.get("after"))).toEqual([null, "3", null]);
  expect(
    api.reads.every(
      (url) =>
        url.searchParams.get("workDate") === "2026-10-12" && url.searchParams.get("limit") === "50",
    ),
  ).toBe(true);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("explicit refresh removes the previous history before admitting a newer correction", async ({
  page,
}) => {
  const api = await installAttendanceHistoryApi(page, 3);
  await page.goto(history);
  await expect(page.getByRole("button", { name: "View correction: 2", exact: true })).toBeVisible();
  const held = api.holdRead();
  api.appendCorrection();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.started;
    await expect(page.getByRole("table", { name: "Corrections", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
    held.release();
    await expect(
      page.getByRole("button", { name: "View correction: 3", exact: true }),
    ).toBeVisible();
    expect(api.reads).toHaveLength(2);
  } finally {
    held.release();
  }
});

test("empty and terminal history remain read-only and do not invent an absence", async ({
  page,
}) => {
  const api = await installAttendanceHistoryApi(page, 0);
  await page.goto(history);
  await expect(page.getByText("No corrections for this work date.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Older corrections", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "First page", exact: true })).toBeDisabled();
  await expect(page.getByRole("table")).toHaveCount(0);
  await page.goto(`${history}?${scope}&after=0`);
  await expect(page.getByText("No corrections for this work date.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "First page", exact: true })).toBeEnabled();
  expect(api.reads).toHaveLength(2);
});

for (const input of ["after=01", "after=-1", "after=9007199254740992"]) {
  test(`invalid history cursor ${input} does not reach the API`, async ({ page }) => {
    const api = await installAttendanceHistoryApi(page);
    await page.goto(`${history}?${scope}&${input}`);
    await expect(page.getByRole("alert")).toBeVisible();
    expect(api.reads).toHaveLength(0);
  });
}

test("company departure discards a pending correction page and its employee reference", async ({
  page,
}) => {
  const api = await installAttendanceHistoryApi(page);
  const held = api.holdRead();
  try {
    await page.goto(`${history}?${scope}`);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(
      new RegExp(`/workforce/attendance\\?company=${companyIds[1]}`, "u"),
    );
    held.release();
    await expect(page.getByRole("table", { name: "Corrections", exact: true })).toHaveCount(0);
    await expect(
      page.getByRole("heading", { name: "Correction history", exact: true }),
    ).toHaveCount(0);
    expect(api.reads).toHaveLength(1);
    expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
  } finally {
    held.release();
  }
});

test("MFA recovery requires a fresh history read and cannot expose the earlier private page", async ({
  page,
}) => {
  const api = await installAttendanceHistoryApi(page, 3);
  await page.goto(history);
  await expect(page.getByRole("table", { name: "Corrections", exact: true })).toBeVisible();
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Corrections", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(2);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Corrections", exact: true })).toBeVisible();
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(3);
});

test("history and its detail panel fit narrow light/dark layouts and localize without refetching", async ({
  page,
}) => {
  const api = await installAttendanceHistoryApi(page, 3);
  await page.setViewportSize({ width: 430, height: 932 });
  await page.goto(history);
  await expect(page.getByRole("button", { name: "View correction: 2", exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-history-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Riwayat koreksi", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Lihat koreksi: 2", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Detail koreksi", exact: true });
  await expect(panel.getByText("Waktu kerja", { exact: true })).toBeVisible();
  await expect(panel.getByText("Asia/Jakarta", { exact: true })).toBeVisible();
  await expect
    .poll(() =>
      panel.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return bounds.left >= 0 && Math.round(bounds.right) <= window.innerWidth;
      }),
    )
    .toBe(true);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-history-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.reads).toHaveLength(1);
  expect(api.identity.unhandled).toHaveLength(0);
});
