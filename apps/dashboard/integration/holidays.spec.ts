import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type {
  HolidayChangeDto,
  HolidayDto,
} from "../src/features/workforce/data/models/holiday-dto";
import { authenticatorCode } from "./fixtures/authenticator";

test("real holiday authoring replays one commit, resolves a competing edit and updates the employee calendar", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-holiday-admin@example.invalid");
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
    data: { code: "BROWSER_HOLIDAY", name: "Browser Holidays", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const base = `/api/v1/companies/${company}`;
  const employee = randomUUID();
  const enrolled = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "HOLIDAY-001",
      person: { id: randomUUID(), legalName: "Holiday Fixture Employee", nationality: "ID" },
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
  const shifted = await context.request.put(`${base}/workforce/shifts/${shift}`, {
    headers: headers(),
    data: {
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
      expectedVersion: null,
      reason: "Calendar fixture",
    },
  });
  expect(shifted.status()).toBe(200);
  const days = Object.fromEntries(
    ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"].map((day) => [
      day,
      { id: shift, version: 0 },
    ]),
  );
  const scheduled = await context.request.put(`${base}/workforce/employees/${employee}/schedule`, {
    headers: headers(),
    data: { effectiveFrom: "2026-01-01", days, reason: "Calendar fixture" },
  });
  expect(scheduled.status()).toBe(200);

  await page.reload();
  await page.getByRole("link", { name: "Holidays", exact: true }).click();
  await page.getByRole("link", { name: "Create holiday", exact: true }).click();
  await page.getByLabel("Date", { exact: true }).fill("2026-10-12");
  await page.getByLabel("Name", { exact: true }).fill(" Company holiday ");
  await page.getByLabel("Reason", { exact: true }).fill("Annual calendar");
  let lost = false;
  const writes: { id: string; operation: string; body: HolidayChangeDto }[] = [];
  await page.route("**/api/v1/companies/*/workforce/holidays/*", async (route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.continue();
    writes.push({
      id: new URL(request.url()).pathname.split("/").at(-1) ?? "",
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON() as HolidayChangeDto,
    });
    if (lost) return route.continue();
    lost = true;
    const committed = await route.fetch();
    expect(committed.status()).toBe(200);
    return route.abort("failed");
  });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Holiday saved.");
  expect(writes).toHaveLength(2);
  expect(writes[1]).toEqual(writes[0]);
  const first = writes[0];
  if (!first) throw new Error("Expected the original holiday request");
  const detailResponse = await context.request.get(`${base}/workforce/holidays/${first.id}`);
  expect(detailResponse.status()).toBe(200);
  const original = (await detailResponse.json()) as HolidayDto;
  expect(original).toMatchObject({
    version: 0,
    name: "Company holiday",
    workDate: "2026-10-12",
    active: true,
  });
  const calendarPath = `${base}/workforce/employees/${employee}/calendar?from=2026-10-12&until=2026-10-12`;
  const holidayCalendarResponse = await context.request.get(calendarPath);
  expect(holidayCalendarResponse.status()).toBe(200);
  expect((await holidayCalendarResponse.json()).days).toEqual([
    expect.objectContaining({
      workDate: "2026-10-12",
      kind: "OFF",
      origin: "HOLIDAY",
      holidayId: first.id,
      plannedMinutes: 0,
    }),
  ]);

  await page.getByRole("link", { name: "View details", exact: true }).click();
  await page.getByRole("link", { name: "Edit holiday", exact: true }).click();
  await page.getByLabel("Name", { exact: true }).fill("My revision");
  await page.getByLabel("Reason", { exact: true }).fill("Calendar correction");
  const competing = await context.request.put(`${base}/workforce/holidays/${first.id}`, {
    headers: headers(),
    data: {
      ...first.body,
      name: "Concurrent holiday",
      expectedVersion: 0,
      reason: "Independent correction",
    },
  });
  expect(competing.status()).toBe(200);
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("My revision");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Concurrent holiday");
  await page.getByLabel("Status", { exact: true }).selectOption("false");
  await page.getByLabel("Reason", { exact: true }).fill("Deactivate holiday");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Holiday saved.");
  expect(writes.map((write) => write.body.expectedVersion)).toEqual([null, null, 0, 1]);
  expect(new Set(writes.map((write) => write.operation)).size).toBe(3);
  const updatedResponse = await context.request.get(`${base}/workforce/holidays/${first.id}`);
  expect(updatedResponse.status()).toBe(200);
  expect(await updatedResponse.json()).toMatchObject({ version: 2, active: false });
  const restoredCalendarResponse = await context.request.get(calendarPath);
  expect(restoredCalendarResponse.status()).toBe(200);
  expect((await restoredCalendarResponse.json()).days).toEqual([
    expect.objectContaining({
      workDate: "2026-10-12",
      kind: "WORK",
      origin: "PATTERN",
      plannedMinutes: 480,
    }),
  ]);
});
