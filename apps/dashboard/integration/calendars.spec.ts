import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type {
  CalendarReviewDto,
  RosterDayChangeDto,
  RosterResetDto,
  WeeklyScheduleChangeDto,
} from "../src/features/workforce/data/models/calendar-dto";
import { authenticatorCode } from "./fixtures/authenticator";

test("real schedule editing preserves future assignments, replays one commit and resolves a competing roster version", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-calendar-admin@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await page.getByRole("button", { name: "I have saved the codes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const created = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "BROWSER_CALENDAR", name: "Browser Calendars", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const base = `/api/v1/companies/${company}`;
  const employee = randomUUID();
  const enrolled = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "CALENDAR-001",
      person: { id: randomUUID(), legalName: "Calendar Fixture Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2026-01-01",
        startDate: "2026-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Calendar fixture",
    },
  });
  expect(enrolled.status()).toBe(200);
  const shift = randomUUID();
  const shiftBody = {
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
    reason: "Calendar fixture",
  };
  const shifted = await context.request.put(`${base}/workforce/shifts/${shift}`, {
    headers: headers(),
    data: { ...shiftBody, expectedVersion: null },
  });
  expect(shifted.status()).toBe(200);
  const employeeBase = `${base}/workforce/employees/${employee}`;
  for (const [effectiveFrom, expectedVersion] of [
    ["2026-01-01", null],
    ["2026-11-01", 0],
  ] as const) {
    const scheduled = await context.request.put(`${employeeBase}/schedule`, {
      headers: headers(),
      data: {
        effectiveFrom,
        expectedVersion,
        days: { MONDAY: { id: shift, version: 0 } },
        reason: "Calendar fixture",
      },
    });
    expect(scheduled.status()).toBe(200);
  }
  const revised = await context.request.put(`${base}/workforce/shifts/${shift}`, {
    headers: headers(),
    data: { ...shiftBody, startsAt: "09:00", endsAt: "18:00", expectedVersion: 0 },
  });
  expect(revised.status()).toBe(200);
  await page.goto(`/workforce/calendars?company=${company}&asOf=2026-10-12`);
  await page
    .getByRole("button", {
      name: "View calendar: Calendar Fixture Employee (CALENDAR-001)",
      exact: true,
    })
    .click();
  await expect(page.getByRole("table").first()).toContainText("08:00–17:00");
  await page.getByRole("link", { name: "Edit weekly schedule", exact: true }).click();
  await expect(page.getByLabel("Monday", { exact: true })).toHaveValue(`${shift}:0`);
  await page.getByLabel("Monday", { exact: true }).selectOption(`${shift}:1`);
  await page.getByLabel("Reason", { exact: true }).fill("Use the revised shift until November");

  const writes: {
    path: string;
    operation: string;
    body: WeeklyScheduleChangeDto | RosterDayChangeDto | RosterResetDto;
  }[] = [];
  let loseWeekly = true;
  let loseReset = false;
  await page.route("**/api/v1/companies/*/workforce/employees/**", async (route) => {
    const request = route.request();
    if (!["PUT", "POST"].includes(request.method())) return route.continue();
    const path = new URL(request.url()).pathname;
    writes.push({
      path,
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON(),
    });
    if (loseWeekly && path.endsWith("/schedule")) {
      loseWeekly = false;
      const committed = await route.fetch();
      expect(committed.status()).toBe(200);
      return route.abort("failed");
    }
    if (loseReset && path.endsWith("/reset")) {
      loseReset = false;
      const committed = await route.fetch();
      expect(committed.status()).toBe(200);
      return route.abort("failed");
    }
    return route.continue();
  });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(writes).toHaveLength(2);
  expect(writes[1]).toEqual(writes[0]);
  expect(writes[0]?.body).toMatchObject({
    expectedVersion: 1,
    days: { MONDAY: { id: shift, version: 1 } },
  });
  const currentResponse = await context.request.get(
    `${employeeBase}/calendar/review?from=2026-10-12&until=2026-10-12`,
  );
  expect(currentResponse.status()).toBe(200);
  const current = (await currentResponse.json()) as CalendarReviewDto;
  expect(current.calendar.scheduleVersion).toBe(2);
  expect(current.assignmentAtStart).toMatchObject({
    effectiveFrom: "2026-10-12",
    revision: 2,
    days: { MONDAY: { revision: 1 } },
  });
  const futureResponse = await context.request.get(
    `${employeeBase}/calendar/review?from=2026-11-02&until=2026-11-02`,
  );
  expect(futureResponse.status()).toBe(200);
  const future = (await futureResponse.json()) as CalendarReviewDto;
  expect(future.assignmentAtStart).toMatchObject({
    effectiveFrom: "2026-11-01",
    revision: 1,
    days: { MONDAY: { revision: 0 } },
  });

  await page.getByRole("link", { name: "View calendar", exact: true }).last().click();
  await page.getByRole("button", { name: "Set daily roster: Oct 13, 2026", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Confirm day off");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(writes[2]?.body).toEqual({
    shift: null,
    expectedVersion: null,
    reason: "Confirm day off",
  });
  await page.getByRole("link", { name: "View calendar", exact: true }).last().click();
  await page.getByRole("button", { name: "Set daily roster: Oct 13, 2026", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("My correction");
  const competing = await context.request.put(`${employeeBase}/roster/2026-10-13`, {
    headers: headers(),
    data: {
      shift: { id: shift, version: 1 },
      expectedVersion: 0,
      reason: "Independent coverage change",
    },
  });
  expect(competing.status()).toBe(200);
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(page.getByLabel("Shift", { exact: true })).toHaveValue(`${shift}:1`);
  await page.getByLabel("Shift", { exact: true }).selectOption("");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed day off");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(writes.slice(2).map((write) => write.body.expectedVersion)).toEqual([null, 0, 1]);
  const rosterResponse = await context.request.get(
    `${employeeBase}/calendar/review?from=2026-10-13&until=2026-10-13`,
  );
  expect(rosterResponse.status()).toBe(200);
  const roster = (await rosterResponse.json()) as CalendarReviewDto;
  expect(roster.calendar.days).toEqual([
    expect.objectContaining({ kind: "OFF", origin: "ROSTER", originVersion: 2 }),
  ]);
  expect(new Set(writes.map((write) => write.operation)).size).toBe(4);

  await page.getByRole("link", { name: "View calendar", exact: true }).last().click();
  await page.getByRole("button", { name: "Set daily roster: Oct 13, 2026", exact: true }).click();
  await page.getByLabel("Shift", { exact: true }).selectOption("calendar");
  await page.getByLabel("Reason", { exact: true }).fill("Restore regular calendar");
  loseReset = true;
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(writes[5]?.body).toEqual({ expectedVersion: 2, reason: "Restore regular calendar" });
  expect(writes[6]).toEqual(writes[5]);
  const resetResponse = await context.request.get(
    `${employeeBase}/calendar/review?from=2026-10-13&until=2026-10-13`,
  );
  expect(resetResponse.status()).toBe(200);
  const reset = (await resetResponse.json()) as CalendarReviewDto;
  expect(reset.calendar.days).toEqual([
    expect.objectContaining({ kind: "OFF", origin: "PATTERN", originVersion: 2 }),
  ]);
  expect(reset.rosterOverrides).toEqual([
    { workDate: "2026-10-13", version: 3, active: false, shift: null },
  ]);

  await page.getByRole("link", { name: "View calendar", exact: true }).last().click();
  await page.getByRole("button", { name: "Set daily roster: Oct 13, 2026", exact: true }).click();
  await expect(page.getByText("Reviewed roster version: 3", { exact: true })).toBeVisible();
  await expect(page.getByRole("option", { name: "Follow calendar", exact: true })).toHaveCount(0);
  await page.getByLabel("Shift", { exact: true }).selectOption(`${shift}:1`);
  await page.getByLabel("Reason", { exact: true }).fill("New coverage after reset");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(writes[7]?.body).toEqual({
    shift: { id: shift, version: 1 },
    expectedVersion: 3,
    reason: "New coverage after reset",
  });
  const reactivatedResponse = await context.request.get(
    `${employeeBase}/calendar/review?from=2026-10-13&until=2026-10-13`,
  );
  expect(reactivatedResponse.status()).toBe(200);
  const reactivated = (await reactivatedResponse.json()) as CalendarReviewDto;
  expect(reactivated.calendar.days).toEqual([
    expect.objectContaining({ kind: "WORK", origin: "ROSTER", originVersion: 4 }),
  ]);
  expect(new Set(writes.map((write) => write.operation)).size).toBe(6);
});
