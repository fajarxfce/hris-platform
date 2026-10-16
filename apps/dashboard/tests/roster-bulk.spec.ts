import { expect, type Page, test } from "@playwright/test";
import { identifier } from "../src/features/workforce/di/calendar-fixture";
import { companyIds } from "./identity-api";
import { installRosterBulkApi } from "./roster-bulk-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-11T12:00:00Z"));
});
const start = "/workforce/calendars/bulk?from=2026-10-12";
const selection = (page: Page) =>
  page.getByRole("region", { name: "Selected employees", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function selectOne(page: Page) {
  await page.getByLabel("Until", { exact: true }).fill("2026-10-13");
  await page
    .getByRole("checkbox", { name: "Select employee: North Employee 1 (EMP-001)", exact: true })
    .check();
}
async function reviewOne(page: Page) {
  await selectOne(page);
  await page.getByRole("button", { name: "Review changes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Schedule changes", exact: true })).toBeFocused();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed coverage");
}

test("bulk entry retains employees and observed shifts across pages and renders an accessible bilingual review", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  await page.goto("/workforce/calendars?asOf=2026-10-12");
  await page.getByRole("link", { name: "Bulk edit roster", exact: true }).click();
  await selectOne(page);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await page
    .getByRole("checkbox", { name: "Select employee: North Employee 51 (EMP-051)", exact: true })
    .check();
  await expect(selection(page)).toContainText("North Employee 1");
  await expect(selection(page)).toContainText("North Employee 51");
  await page.getByLabel("Schedule", { exact: true }).selectOption("SHIFT");
  const shifts = page.getByRole("region", { name: "Shift", exact: true });
  await shifts.getByLabel("Shift", { exact: true }).selectOption(`${identifier()}:0`);
  await shifts.getByRole("button", { name: "Next page", exact: true }).click();
  await shifts.getByLabel("Shift", { exact: true }).selectOption(`${identifier(51)}:0`);
  await shifts.getByLabel("Search shifts", { exact: true }).fill("Shift 55");
  await shifts.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(shifts.getByLabel("Shift", { exact: true })).toHaveValue(`${identifier(51)}:0`);
  await page.getByRole("button", { name: "Review changes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Schedule changes", exact: true })).toBeFocused();
  await expect(
    page.getByRole("table", { name: "Entries", exact: true }).getByRole("row"),
  ).toHaveCount(5);
  await expect(page.getByRole("table", { name: "Entries", exact: true })).toContainText("Shift 51");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed coverage");
  await page.screenshot({ path: "../../.work/dashboard-roster-bulk-light.png", fullPage: true });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => {
    const typography = [...document.querySelectorAll<HTMLElement>(".app-roster-bulk *")].map(
      (element) => {
        const style = getComputedStyle(element);
        return {
          element,
          font: Number.parseFloat(style.fontSize),
          line: Number.parseFloat(style.lineHeight),
        };
      },
    );
    for (const { element, font, line } of typography) {
      element.style.fontSize = `${font * 2}px`;
      if (Number.isFinite(line)) element.style.lineHeight = `${line * 2}px`;
    }
  });
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Reviewed coverage");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-roster-bulk-dark-mobile.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Simpan perubahan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Perubahan roster tersimpan.");
  expect(api.writes[0]?.body).toMatchObject({
    selection: {
      employeeIds: [identifier(501), identifier(551)],
      dates: ["2026-10-12", "2026-10-13"],
      mode: "SHIFT",
      shift: { id: identifier(51), version: 0 },
    },
    reason: "Reviewed coverage",
  });
  expect(api.commits).toBe(1);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("an unknown save retains one command through MFA and a later conflict without automatic resubmission", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  await page.goto(start);
  await reviewOne(page);
  api.loseNextSave();
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result could not be confirmed");
  await expect(page.getByRole("button", { name: "Edit selection", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNextSave("mfa_required");
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  expect(api.writes).toHaveLength(2);
  await expect(page.getByRole("table", { name: "Entries", exact: true })).toHaveCount(0);
  api.rejectNextSave("roster_bulk_changed");
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result could not be confirmed");
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Roster changes saved.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  expect(api.writes.every((write) => JSON.stringify(write) === JSON.stringify(api.writes[0]))).toBe(
    true,
  );
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
});

test("a competing revision requires another preview and a distinct confirmed operation", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  await page.goto(start);
  await reviewOne(page);
  api.changeRevision();
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "The reviewed employment or schedules changed",
  );
  await expect(page.getByRole("button", { name: "Save changes", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Edit selection", exact: true }).click();
  await expect(selection(page)).toContainText("North Employee 1");
  await page.getByRole("button", { name: "Review changes", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Reviewed coverage");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Roster changes saved.");
  expect(api.writes).toHaveLength(2);
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.writes[0]?.body.reviewRevision).not.toBe(api.writes[1]?.body.reviewRevision);
});

test("unchanged schedules have a readable preview and no save action", async ({ page }) => {
  const api = await installRosterBulkApi(page);
  api.unchanged();
  await page.goto(start);
  await reviewOne(page);
  await expect(page.getByRole("status")).toContainText("No changes are needed");
  await expect(page.getByRole("button", { name: "Save changes", exact: true })).toBeDisabled();
  expect(api.writes).toHaveLength(0);
  await page.getByRole("button", { name: "Edit selection", exact: true }).click();
  await page.getByLabel("Schedule", { exact: true }).selectOption("CALENDAR");
  await page.getByRole("button", { name: "Review changes", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save changes", exact: true })).toBeDisabled();
  await expect(page.getByRole("table", { name: "Entries", exact: true })).toContainText(
    "Unchanged",
  );
});

test("employee and entry limits are explicit without losing previous page selections", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  await page.goto(start);
  await page.getByRole("checkbox", { name: "Select this page", exact: true }).check();
  await expect(selection(page)).toContainText("Entries: 350 / 200");
  await expect(page.getByRole("button", { name: "Review changes", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await page
    .getByRole("checkbox", { name: "Select employee: North Employee 51 (EMP-051)", exact: true })
    .click();
  await expect(
    selection(page).getByRole("alert").filter({ hasText: "Select up to 50 employees." }),
  ).toBeVisible();
  await expect(selection(page).getByRole("heading")).toHaveText("Selected employees (50)");
  await page.getByLabel("Until", { exact: true }).fill("2026-10-15");
  await expect(selection(page)).toContainText("Entries: 200 / 200");
  await page.getByRole("button", { name: "Review changes", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Entries", exact: true }).getByRole("row"),
  ).toHaveCount(201);
  expect(api.reviews[0]?.selection.employeeIds).toHaveLength(50);
  expect(api.reviews[0]?.selection.dates).toHaveLength(4);
});

test("a pending company preview cannot repopulate another workspace after an approved departure", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  await page.goto(start);
  await selectOne(page);
  const pending = api.holdReview();
  try {
    await page.getByRole("button", { name: "Review changes", exact: true }).click();
    await pending.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("combobox", { name: "Company", exact: true })).toHaveValue(
      companyIds[1],
    );
    pending.release();
    await page.getByRole("link", { name: "Bulk edit roster", exact: true }).click();
    await expect(selection(page)).toContainText("No employees selected.");
    await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "South Employee 1",
    );
    await expect(page.getByRole("heading", { name: "Schedule changes", exact: true })).toHaveCount(
      0,
    );
    expect(api.reviews).toHaveLength(1);
    expect(api.writes).toHaveLength(0);
  } finally {
    pending.release();
  }
});

test("a pending save disables duplicate commands and leaving can be cancelled without losing the result", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  await page.goto(start);
  await reviewOne(page);
  const pending = api.holdSave();
  try {
    await page.getByRole("button", { name: "Save changes", exact: true }).click();
    await pending.started;
    await expect(page.getByRole("button", { name: "Save changes", exact: true })).toHaveCount(0);
    await page.getByRole("main").getByRole("link", { name: "Work calendars", exact: true }).click();
    await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
    pending.release();
    await expect(page.getByRole("status")).toContainText("Roster changes saved.");
    expect(api.writes).toHaveLength(1);
    expect(api.commits).toBe(1);
  } finally {
    pending.release();
  }
});

test("read-only scope hides the entry and a direct route starts no employee or review request", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page, ["workforce.read"]);
  await page.goto("/workforce/calendars?asOf=2026-10-12");
  await expect(page.getByRole("table", { name: "Employee calendars", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Bulk edit roster", exact: true })).toHaveCount(0);
  api.reads.splice(0);
  await page.goto(start);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByRole("button", { name: "Review changes", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  expect(api.reviews).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});

test("changing schedule mode cancels the now hidden shift catalog", async ({ page }) => {
  const api = await installRosterBulkApi(page);
  await page.goto(start);
  const pending = api.holdShifts();
  try {
    await page.getByLabel("Schedule", { exact: true }).selectOption("SHIFT");
    await pending.started;
    const cancelled = page.waitForEvent("requestfailed", {
      predicate: (request) => new URL(request.url()).pathname.endsWith("/workforce/shifts"),
    });
    await page.getByLabel("Schedule", { exact: true }).selectOption("DAY_OFF");
    await cancelled;
    pending.release();
    await expect(page.getByRole("region", { name: "Shift", exact: true })).toHaveCount(0);
    await expect(page.getByLabel("Schedule", { exact: true })).toHaveValue("DAY_OFF");
    expect(api.reviews).toHaveLength(0);
  } finally {
    pending.release();
  }
});

test("an older API keeps calendars usable without advertising an unsupported bulk workflow", async ({
  page,
}) => {
  const api = await installRosterBulkApi(page);
  api.legacyApi();
  await page.goto("/workforce/calendars?asOf=2026-10-12");
  await expect(page.getByRole("table", { name: "Employee calendars", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Bulk edit roster", exact: true })).toHaveCount(0);
  await page.goto(start);
  await expect(page.getByRole("alert")).toContainText(
    "Bulk roster editing is unavailable on this server.",
  );
  await expect(page.getByRole("checkbox", { name: "Select this page", exact: true })).toHaveCount(
    0,
  );
  expect(api.reviews).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});
