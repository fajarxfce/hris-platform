import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installLeaveCalendarApi } from "./leave-calendar-api";

const path = `/leave/calendar?company=${companyIds[0]}&scope=COMPANY`;
const calendar = (page: Page) => page.getByRole("table", { name: "October 2026", exact: true });
const entries = (page: Page) =>
  page.getByRole("table", { name: "Leave on selected date", exact: true });
const day = (page: Page, number: number) =>
  calendar(page).getByRole("button", { name: new RegExp(`October ${number}, 2026,`, "u") });

test("date selection and bounded pages retain the month; detail and history return to the same calendar", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(path);
  await expect(entries(page).getByRole("row")).toHaveCount(21);
  await expect(day(page, 15)).toHaveAttribute("aria-pressed", "true");
  await expect(day(page, 15)).toHaveAttribute("aria-current", "date");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(entries(page).getByRole("row")).toHaveCount(3);
  expect(api.reads.filter((read) => read.kind === "month")).toHaveLength(1);
  const selected = page.url();
  await entries(page)
    .getByRole("button", { name: "View request: North Employee 22", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "North Employee 22",
  );
  await page.getByRole("link", { name: "Back to calendar", exact: true }).click();
  await expect(entries(page).getByRole("row")).toHaveCount(3);
  const returned = new URL(page.url()),
    before = new URL(selected);
  expect(returned.searchParams.get("after")).toBe(before.searchParams.get("after"));
  expect(returned.searchParams.get("month")).toBe("2026-10");
  await day(page, 5).focus();
  await page.keyboard.press("Enter");
  await expect(entries(page).getByRole("row")).toHaveCount(3);
  await expect(
    page.getByRole("region", { name: "Monday, October 5, 2026", exact: true }),
  ).toContainText("1 employee · 2 requests");
  const reads = api.reads.filter((read) => read.kind === "month").length;
  await entries(page)
    .getByRole("button", { name: "View request: North Employee 01", exact: true })
    .first()
    .click();
  await page.getByRole("button", { name: "Older changes", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Request history", exact: true }).getByRole("row"),
  ).toHaveCount(5);
  await page.getByRole("link", { name: "Back to calendar", exact: true }).click();
  await expect(day(page, 5)).toHaveAttribute("aria-pressed", "true");
  expect(api.reads.filter((read) => read.kind === "month")).toHaveLength(reads + 1);
  expect(api.identity.unhandled).toEqual([]);
  expect(
    await entries(page).evaluate(
      (table) =>
        table.parentElement !== null && table.scrollWidth <= table.parentElement.clientWidth,
    ),
  ).toBe(true);
  await page.screenshot({ path: "/tmp/hris-leave-calendar/calendar-desktop.png", fullPage: true });
});

test("month controls use company time, support leap days and keep drafts through appearance changes", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page);
  await page.clock.setFixedTime(new Date("2026-10-14T01:00:00Z"));
  await page.goto(path);
  await expect(day(page, 15)).toHaveAttribute("aria-current", "date");
  await page.getByLabel("Month", { exact: true }).fill("2028-02");
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByLabel("Month", { exact: true })).toHaveValue("2028-02");
  await page.getByRole("button", { name: "Go to month", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "February 2028", exact: true }).getByRole("button"),
  ).toHaveCount(29);
  await expect(page.getByRole("status")).toContainText("No leave on this date.");
  await page.getByRole("button", { name: "Previous month", exact: true }).click();
  await expect(page.getByRole("table", { name: "January 2028", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Today", exact: true }).click();
  await expect(day(page, 15)).toHaveAttribute("aria-pressed", "true");
  expect(api.reads.at(-2)?.url.searchParams.has("month")).toBe(false);
});

test("team readers default to current reports without acquiring company scope", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page, ["leave.team.read"]);
  await page.goto(`/leave/calendar?company=${companyIds[0]}`);
  await expect(entries(page)).toContainText("North Employee 01");
  await expect(entries(page)).not.toContainText("North Employee 02");
  await expect(page.getByRole("combobox", { name: "Scope", exact: true })).toHaveValue("TEAM");
  await expect(
    page
      .getByRole("combobox", { name: "Scope", exact: true })
      .getByRole("option", { name: "Company", exact: true }),
  ).toHaveCount(0);
  expect(api.reads.every((read) => read.url.searchParams.get("scope") === "TEAM")).toBe(true);
  await page.goto(path);
  await expect(page.getByRole("alert")).toContainText(/permission|access/iu);
  expect(api.reads).toHaveLength(2);
});
for (const permissions of [["leave.self.read"], ["leave.approve", "approvals.read"]]) {
  test(`calendar denies ${permissions.join(",")} before I/O`, async ({ page }) => {
    const api = await installLeaveCalendarApi(page, permissions);
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText(/permission|access/iu);
    expect(api.reads).toHaveLength(0);
    await expect(calendar(page)).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Leave calendar", exact: true })).toHaveCount(0);
  });
}
for (const kind of ["month", "date"] as const) {
  test(`company replacement discards a late ${kind} response and all old selections`, async ({
    page,
  }) => {
    const api = await installLeaveCalendarApi(page),
      held = api.hold(kind);
    try {
      await page.goto(`${path}&month=2026-10&date=2026-10-05`);
      await held.entered;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(entries(page)).toContainText("South Employee 01");
      const delivered = api.delivered;
      held.release();
      await expect.poll(() => api.delivered).toBe(delivered + 1);
      await expect(entries(page)).not.toContainText("North Employee");
      await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}&scope=COMPANY$`, "u"));
      expect(api.identity.unhandled).toEqual([]);
    } finally {
      held.release();
    }
  });
}

test("replacing a pending date does not move the selection back when it finishes", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page);
  await page.goto(path);
  await expect(entries(page)).toBeVisible();
  const held = api.hold("date");
  try {
    await day(page, 5).click();
    await held.entered;
    await day(page, 6).click();
    await expect(page.getByRole("status")).toContainText("No leave on this date.");
    const delivered = api.delivered;
    held.release();
    await expect.poll(() => api.delivered).toBe(delivered + 1);
    await expect(day(page, 6)).toHaveAttribute("aria-pressed", "true");
    await expect(entries(page)).toHaveCount(0);
    expect(api.reads.filter((read) => read.kind === "month")).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("a day access denial clears the entire calendar; MFA recovery reacquires authorized data", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page);
  await page.goto(path);
  await expect(entries(page)).toBeVisible();
  api.identity.expireMfa();
  api.reject("date", "mfa_required");
  await day(page, 5).click();
  await expect(calendar(page)).toHaveCount(0);
  await expect(entries(page)).toHaveCount(0);
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  api.reject("date", null);
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(day(page, 5)).toHaveAttribute("aria-pressed", "true");
  await expect(entries(page)).toContainText("North Employee 01");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
});

test("transient date failures preserve the month; Indonesian dark layout stays within the viewport", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page);
  await page.goto(`${path}&date=2026-10-05`);
  await expect(entries(page)).toBeVisible();
  api.reject("date", "network_unavailable", 503);
  await day(page, 6).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(calendar(page)).toBeVisible();
  api.reject("date", null);
  await day(page, 5).click();
  await expect(entries(page)).toBeVisible();
  const reads = api.reads.length;
  await page.getByLabel("Month", { exact: true }).fill("2028-02");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Bulan", { exact: true })).toHaveValue("2028-02");
  await expect(
    page.getByRole("table", { name: "Cuti pada tanggal terpilih", exact: true }),
  ).toContainText("Paruh pertama");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({
    path: "/tmp/hris-leave-calendar/calendar-mobile-id.png",
    fullPage: true,
  });
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "200%";
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("Today renews company time after midnight even when only the selected date changed", async ({
  page,
}) => {
  const api = await installLeaveCalendarApi(page);
  await page.goto(`${path}&date=2026-10-05`);
  await expect(day(page, 5)).toHaveAttribute("aria-pressed", "true");
  api.date("2026-11-01");
  await page.getByRole("button", { name: "Today", exact: true }).click();
  await expect(
    page
      .getByRole("table", { name: "November 2026", exact: true })
      .getByRole("button", { name: /November 1, 2026,/u }),
  ).toHaveAttribute("aria-current", "date");
  await expect.poll(() => api.reads.filter((read) => read.kind === "month").length).toBe(2);
  await expect
    .poll(() => api.reads.findLast((read) => read.kind === "date")?.url.searchParams.get("date"))
    .toBe("2026-11-01");
});
