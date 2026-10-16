import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import type { CalendarReviewDto } from "../src/features/workforce/data/models/calendar-dto";
import type {
  RosterBulkApplyDto,
  RosterBulkReviewDto,
} from "../src/features/workforce/data/models/roster-bulk-dto";
import { createAdminCompany } from "./fixtures/admin-company";

async function selectEmployees(
  page: Page,
  company: string,
  mode: "DAY_OFF" | "CALENDAR" = "DAY_OFF",
) {
  await page.goto(`/workforce/calendars?company=${company}&asOf=2026-10-12`);
  await page.getByRole("link", { name: "Bulk edit roster", exact: true }).click();
  await page.getByLabel("Until", { exact: true }).fill("2026-10-13");
  await page.getByRole("checkbox", { name: "Select this page", exact: true }).check();
  await page.getByLabel("Schedule", { exact: true }).selectOption(mode);
}
async function reviewSelection(
  page: Page,
  reviews: readonly RosterBulkReviewDto[],
): Promise<RosterBulkReviewDto> {
  const count = reviews.length;
  await page.getByRole("button", { name: "Review changes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Schedule changes", exact: true })).toBeVisible();
  expect(reviews).toHaveLength(count + 1);
  const review = reviews[count];
  if (!review) throw new Error("Missing real API review");
  return review;
}

test("real bulk editing recovers one commit, retains unchanged heads, resets calendars and rejects stale previews atomically", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-roster-bulk-admin@example.invalid",
    "BROWSER_ROSTER_BULK",
    "Roster Fixture Company",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const base = `/api/v1/companies/${company}`;
  const employees = [randomUUID(), randomUUID()];
  const first = employees[0];
  if (!first) throw new Error("Missing employee");
  const shift = randomUUID();
  const shifted = await context.request.put(`${base}/workforce/shifts/${shift}`, {
    headers: headers(),
    data: {
      expectedVersion: null,
      code: "DAY",
      name: "Day shift",
      startsAt: "08:00",
      endsAt: "17:00",
      breakMinutes: 60,
      timezone: "Asia/Jakarta",
      mode: "ONSITE",
      locationRequired: false,
      maxAccuracyMeters: 100,
      fence: null,
      active: true,
      reason: "Fixture shift",
    },
  });
  expect(shifted.status()).toBe(200);
  for (const [index, id] of employees.entries()) {
    const enrolled = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id,
        employeeNumber: `BULK-${index + 1}`,
        person: { id: randomUUID(), legalName: `Roster Employee ${index + 1}`, nationality: "ID" },
        terms: {
          effectiveFrom: "2026-01-01",
          startDate: "2026-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Roster fixture",
      },
    });
    expect(enrolled.status()).toBe(200);
    const scheduled = await context.request.put(`${base}/workforce/employees/${id}/schedule`, {
      headers: headers(),
      data: {
        effectiveFrom: "2026-01-01",
        expectedVersion: null,
        days: { MONDAY: { id: shift, version: 0 }, TUESDAY: { id: shift, version: 0 } },
        reason: "Regular week",
      },
    });
    expect(scheduled.status()).toBe(200);
  }
  const preset = await context.request.put(
    `${base}/workforce/employees/${first}/roster/2026-10-13`,
    {
      headers: headers(),
      data: { expectedVersion: null, shift: null, reason: "Existing day off" },
    },
  );
  expect(preset.status()).toBe(200);
  const calendar = async (id: string): Promise<CalendarReviewDto> => {
    const response = await context.request.get(
      `${base}/workforce/employees/${id}/calendar/review?from=2026-10-12&until=2026-10-13`,
    );
    expect(response.status()).toBe(200);
    return response.json() as Promise<CalendarReviewDto>;
  };
  const writes: { operation: string; body: RosterBulkApplyDto }[] = [];
  const reviews: RosterBulkReviewDto[] = [];
  await page.route("**/api/v1/companies/*/workforce/roster/bulk/review", async (route) => {
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    reviews.push((await response.json()) as RosterBulkReviewDto);
    return route.fulfill({ response });
  });
  let lose = true;
  await page.route("**/api/v1/companies/*/workforce/roster/bulk", async (route) => {
    const request = route.request();
    writes.push({
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON(),
    });
    if (lose) {
      lose = false;
      const committed = await route.fetch();
      expect(committed.status()).toBe(200);
      return route.abort("failed");
    }
    return route.continue();
  });
  await selectEmployees(page, company);
  const initial = await reviewSelection(page, reviews);
  expect(initial.changeCount).toBe(3);
  expect(
    initial.employees.flatMap((entry) => entry.days).filter((day) => !day.changed),
  ).toHaveLength(1);
  await page.getByLabel("Reason", { exact: true }).fill("Shared day off");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result could not be confirmed");
  for (const id of employees) {
    const current = await calendar(id);
    expect(current.rosterOverrides).toEqual([
      { workDate: "2026-10-12", version: 0, active: true, shift: null },
      { workDate: "2026-10-13", version: 0, active: true, shift: null },
    ]);
  }
  const later = await context.request.put(
    `${base}/workforce/employees/${first}/roster/2026-10-12`,
    {
      headers: headers(),
      data: { expectedVersion: 0, shift: { id: shift, version: 0 }, reason: "Later coverage" },
    },
  );
  expect(later.status()).toBe(200);
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Roster changes saved.");
  expect(writes).toHaveLength(2);
  expect(writes[1]).toEqual(writes[0]);
  expect((await calendar(first)).calendar.days[0]).toMatchObject({
    kind: "WORK",
    origin: "ROSTER",
    originVersion: 1,
  });

  await selectEmployees(page, company, "CALENDAR");
  const resetReview = await reviewSelection(page, reviews);
  expect(resetReview.changeCount).toBe(4);
  await page.getByLabel("Reason", { exact: true }).fill("Restore weekly schedules");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Roster changes saved.");
  for (const id of employees) {
    const current = await calendar(id);
    expect(
      current.calendar.days.every((day) => day.kind === "WORK" && day.origin === "PATTERN"),
    ).toBe(true);
    expect(current.rosterOverrides).toEqual([
      { workDate: "2026-10-12", version: id === first ? 2 : 1, active: false, shift: null },
      { workDate: "2026-10-13", version: 1, active: false, shift: null },
    ]);
  }

  await selectEmployees(page, company);
  await reviewSelection(page, reviews);
  await page.getByLabel("Reason", { exact: true }).fill("New day off");
  const competing = await context.request.put(
    `${base}/workforce/employees/${first}/roster/2026-10-12`,
    {
      headers: headers(),
      data: { expectedVersion: 2, shift: { id: shift, version: 0 }, reason: "Competing roster" },
    },
  );
  expect(competing.status()).toBe(200);
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "The reviewed employment or schedules changed",
  );
  for (const id of employees) {
    const current = await calendar(id);
    expect(current.rosterOverrides).toEqual([
      expect.objectContaining({
        workDate: "2026-10-12",
        version: id === first ? 3 : 1,
        active: id === first,
      }),
      { workDate: "2026-10-13", version: 1, active: false, shift: null },
    ]);
  }
  await page.getByRole("button", { name: "Edit selection", exact: true }).click();
  const revised = await reviewSelection(page, reviews);
  expect(revised.changeCount).toBe(4);
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Roster changes saved.");
  expect(writes).toHaveLength(5);
  expect(new Set(writes.map((write) => write.operation)).size).toBe(4);
  for (const id of employees) {
    const current = await calendar(id);
    expect(current.rosterOverrides).toEqual([
      { workDate: "2026-10-12", version: id === first ? 4 : 2, active: true, shift: null },
      { workDate: "2026-10-13", version: 2, active: true, shift: null },
    ]);
  }
  const missingCsrf = await context.request.post(`${base}/workforce/roster/bulk/review`, {
    data: { employeeIds: employees, dates: ["2026-10-12"], mode: "DAY_OFF", shift: null },
  });
  expect(missingCsrf.status()).toBe(403);
});
