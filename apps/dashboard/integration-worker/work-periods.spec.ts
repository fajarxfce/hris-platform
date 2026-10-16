import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import type { WorkPeriodOverviewDto } from "../src/features/workforce/data/models/work-period-overview-dto";
import type { WorkPeriodSnapshotDto } from "../src/features/workforce/data/models/work-period-snapshot-dto";
import { exerciseAttendanceAdjustments } from "./fixtures/attendance-adjustment-workflow";

test("closing reviews offline evidence, replays original receipts and publishes immutable employee results", async ({
  page,
  context,
  request,
}) => {
  test.setTimeout(180_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-closing-admin@example.invalid",
    "CLOSING",
    "Attendance Review Company",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const base = `/api/v1/companies/${company}`,
    account = "a0000000-0000-4000-8000-000000000028";
  const calendarResponse = await context.request.get(`${base}/workforce/periods/2026-01`);
  expect(calendarResponse.status()).toBe(200);
  const companyDate = ((await calendarResponse.json()) as WorkPeriodOverviewDto).companyDate;
  const currentMonth = new Date(`${companyDate.slice(0, 7)}-01T00:00:00Z`);
  const monthEnd = new Date(currentMonth.getTime() - 86_400_000).toISOString().slice(0, 10);
  const month = monthEnd.slice(0, 7),
    workDate = `${month}-01`;
  const employee = randomUUID(),
    futureEmployee = randomUUID(),
    shift = randomUUID(),
    night = randomUUID(),
    event = randomUUID();
  expect(
    (
      await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["attendance.self.record"],
          reason: "Own attendance fixture",
        },
      })
    ).status(),
  ).toBe(200);
  for (const [id, number, name, startDate, accountId] of [
    [employee, "CLOSING-001", "Monthly Attendance Employee", "2025-01-01", account],
    [futureEmployee, "CLOSING-002", "Later Hire", `${companyDate.slice(0, 7)}-01`, null],
  ] as const) {
    expect(
      (
        await context.request.post(`${base}/employees`, {
          headers: headers(),
          data: {
            id,
            employeeNumber: number,
            person: {
              id: randomUUID(),
              legalName: name,
              nationality: "ID",
              ...(accountId ? { accountId } : {}),
            },
            terms: { effectiveFrom: startDate, startDate, contract: "PERMANENT", status: "ACTIVE" },
            reason: "Monthly closing fixture",
          },
        })
      ).status(),
    ).toBe(200);
  }
  for (const [id, code, name, startsAt, endsAt] of [
    [shift, "DAY", "Daytime", "08:00", "17:00"],
    [night, "NIGHT", "Nighttime", "22:00", "06:00"],
  ] as const) {
    expect(
      (
        await context.request.put(`${base}/workforce/shifts/${id}`, {
          headers: headers(),
          data: {
            code,
            name,
            startsAt,
            endsAt,
            breakMinutes: 60,
            timezone: "Asia/Jakarta",
            mode: "REMOTE",
            locationRequired: false,
            reason: "Closing schedule",
          },
        })
      ).status(),
    ).toBe(200);
  }
  const employeeBase = `${base}/workforce/employees/${employee}`;
  for (const [day, shiftId] of [
    [workDate, shift],
    [`${month}-02`, shift],
    [`${month}-03`, shift],
    [monthEnd, night],
  ]) {
    expect(
      (
        await context.request.put(`${employeeBase}/roster/${day}`, {
          headers: headers(),
          data: { shift: { id: shiftId, version: 0 }, reason: "Closing roster" },
        })
      ).status(),
    ).toBe(200);
  }
  expect(
    (
      await context.request.put(`${base}/workforce/holidays/${randomUUID()}`, {
        headers: headers(),
        data: { workDate: `${month}-04`, name: "Company holiday", reason: "Closing calendar" },
      })
    ).status(),
  ).toBe(200);
  const correctedDay = {
    workDate,
    clockIn: `${workDate}T01:00:00Z`,
    clockOut: `${workDate}T09:30:00Z`,
    breakMinutes: 60,
    reason: "Verified working time",
  };
  expect(
    (
      await context.request.post(`${employeeBase}/attendance/corrections`, {
        headers: headers(),
        data: correctedDay,
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${employeeBase}/attendance/corrections`, {
        headers: headers(),
        data: { workDate: `${month}-02`, reason: "Verified absence" },
      })
    ).status(),
  ).toBe(200);
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-closing-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Closing integration",
    },
  });
  expect(native.status()).toBe(200);
  const { credentials } = (await native.json()) as { credentials: { accessToken: string } };
  const nativeHeaders = {
    Authorization: `Bearer ${credentials.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  // The final instant of last month remains within the 31-day capture window this month.
  expect(
    (
      await request.post(`${employeeBase}/attendance`, {
        headers: { ...nativeHeaders, "Idempotency-Key": randomUUID() },
        data: {
          id: event,
          workDate: monthEnd,
          kind: "CLOCK_IN",
          capturedAt: new Date(`${monthEnd}T23:59:59.999+07:00`).toISOString(),
          deviceId: randomUUID(),
          offline: true,
        },
      })
    ).status(),
  ).toBe(200);

  const writes: { operation: string; body: object }[] = [];
  let lose = true;
  await page.route(`**${base}/workforce/periods/${month}/close`, async (route) => {
    writes.push({
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON() as object,
    });
    if (!lose) return route.continue();
    lose = false;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    await response.dispose();
    return route.abort("failed");
  });
  const path = `/workforce/periods/${month}?company=${company}`;
  await page.goto(path);
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Review attendance month");
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await page.getByRole("button", { name: "Retry this request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Closing started.");
  expect(writes).toHaveLength(2);
  expect(writes[1]).toEqual(writes[0]);
  await page.getByRole("link", { name: "View period", exact: true }).click();
  await expect(page.getByRole("region", { name: "Period overview", exact: true })).toContainText(
    "Review required",
    { timeout: 30_000 },
  );
  await expect(page.getByRole("alert")).toContainText(
    "Resolve pending attendance verification before continuing",
  );
  await expect(
    page.getByRole("table", { name: "Published employee results", exact: true }),
  ).toHaveCount(0);
  const attempt = (await (
    await context.request.get(`${base}/workforce/periods/${month}`)
  ).json()) as WorkPeriodOverviewDto;
  expect(attempt.period).toMatchObject({ status: "REVIEW_REQUIRED", version: 2 });
  const firstWrite = writes[0];
  if (!firstWrite) throw new Error("Missing closing request");
  const replay = await context.request.post(`${base}/workforce/periods/${month}/close`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": firstWrite.operation },
    data: firstWrite.body,
  });
  expect(replay.status()).toBe(200);
  expect(await replay.json()).toMatchObject({ id: attempt.attempt?.id, version: 1 });
  expect(
    (
      await context.request.post(`${base}/workforce/attendance/${event}/review`, {
        headers: headers(),
        data: { version: 0, decision: "REJECT", reason: "Unverified offline evidence rejected" },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Verification complete");
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await page.getByRole("link", { name: "View period", exact: true }).click();
  const directory = page.getByRole("table", { name: "Published employee results", exact: true });
  await expect(directory).toBeVisible({ timeout: 30_000 });
  await expect(directory.getByRole("row")).toHaveCount(2);
  await expect(directory).not.toContainText("Later Hire");
  expect(writes).toHaveLength(3);
  expect(writes[2]?.operation).not.toBe(firstWrite.operation);
  expect(writes[2]?.body).toMatchObject({ expectedVersion: 2 });
  const published = (await (
    await context.request.get(`${base}/workforce/periods/${month}`)
  ).json()) as WorkPeriodOverviewDto;
  expect(published.period).toMatchObject({ status: "CLOSED", version: 4 });
  // Progress counts the captured company roster. Discovery only exposes
  // employees with an employment reference at the end of this historical month.
  expect(published.attempt).toMatchObject({
    status: "SUCCEEDED",
    totalEmployees: 2,
    completedEmployees: 2,
  });
  expect(
    (
      await context.request.get(`${base}/workforce/periods/${month}/employees/${futureEmployee}`)
    ).status(),
  ).toBe(404);
  await directory
    .getByRole("button", {
      name: "View result: Monthly Attendance Employee (CLOSING-001)",
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("region", { name: "Accepted attendance (minutes)", exact: true }),
  ).toContainText("450");
  await expect(
    page.getByRole("region", { name: "Recorded absence (days)", exact: true }),
  ).toContainText("1");
  await expect(page.getByRole("region", { name: "Unrecorded days", exact: true })).toContainText(
    "2",
  );
  await page.screenshot({
    path: "../../.work/dashboard-work-period-real-api.png",
    animations: "disabled",
  });
  const snapshotPath = `${base}/workforce/periods/${month}/employees/${employee}`;
  const before = (await (await context.request.get(snapshotPath)).json()) as WorkPeriodSnapshotDto;
  expect(before.days.find((day) => day.workDate === workDate)).toMatchObject({
    fact: "WORKED",
    acceptedMinutes: 450,
  });
  expect(before.days.find((day) => day.workDate === `${month}-02`)).toMatchObject({
    fact: "ABSENCE_RECORDED",
    acceptedMinutes: 0,
  });
  expect(before.days.find((day) => day.workDate === `${month}-03`)).toMatchObject({
    fact: "UNRECORDED",
  });
  expect(before.days.find((day) => day.workDate === `${month}-04`)).toMatchObject({ fact: "OFF" });
  const locked = await context.request.post(`${employeeBase}/attendance/corrections`, {
    headers: headers(),
    data: {
      ...correctedDay,
      expectedVersion: 0,
      clockOut: `${workDate}T10:00:00Z`,
      reason: "Attempt to rewrite a published result",
    },
  });
  expect(locked.status()).toBe(409);
  expect(await locked.json()).toMatchObject({ code: "work_period_locked" });
  expect(await (await context.request.get(snapshotPath)).json()).toEqual(before);
  const ownSnapshot = await request.get(snapshotPath, { headers: nativeHeaders });
  expect(ownSnapshot.status()).toBe(200);
  expect(await ownSnapshot.json()).toEqual(before);
  expect(
    (
      await request.get(`${base}/workforce/periods/${month}/employees`, { headers: nativeHeaders })
    ).status(),
  ).toBe(403);
  await exerciseAttendanceAdjustments({
    page,
    admin: context,
    request,
    company,
    employee,
    workDate: monthEnd,
    original: before,
    nativeHeaders,
    headers,
  });
});
