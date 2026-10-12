import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type {
  AttendanceCorrectionDto,
  AttendanceDayDto,
} from "../src/features/workforce/data/models/attendance-dto";
import type { AttendanceExceptionPageDto } from "../src/features/workforce/data/models/attendance-exception-dto";
import { authenticatorCode } from "./fixtures/authenticator";

test("offline evidence requires independent review, replays one decision, and preserves raw punches through a competing correction", async ({
  page,
  context,
  request,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-attendance-admin@example.invalid");
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
    data: { code: "BROWSER_ATTENDANCE", name: "Browser Attendance", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const base = `/api/v1/companies/${company}`;
  const account = "a0000000-0000-4000-8000-000000000015";
  expect(
    (
      await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["attendance.self.record"],
          reason: "Employee attendance access",
        },
      })
    ).status(),
  ).toBe(200);
  const employee = randomUUID();
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "ATTENDANCE-001",
          person: {
            id: randomUUID(),
            accountId: account,
            legalName: "Attendance Fixture Employee",
            nationality: "ID",
          },
          terms: {
            effectiveFrom: "2026-01-01",
            startDate: "2026-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Attendance fixture",
        },
      })
    ).status(),
  ).toBe(200);
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-attendance-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Attendance integration",
    },
  });
  expect(native.status()).toBe(200);
  const { credentials } = (await native.json()) as { credentials: { accessToken: string } };
  const nativeHeaders = {
    Authorization: `Bearer ${credentials.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  const directory = await request.get(`${base}/workforce/me/employments`, {
    headers: nativeHeaders,
  });
  expect(directory.status()).toBe(200);
  const { asOf } = (await directory.json()) as { asOf: string };
  // Yesterday's overnight shift can still be in progress before 06:00 company time.
  // Both captured events must be in the past, including runs just after midnight.
  const workDate = new Date(Date.parse(`${asOf}T00:00:00Z`) - 2 * 86_400_000)
    .toISOString()
    .slice(0, 10);
  const clockOutDate = new Date(Date.parse(`${workDate}T00:00:00Z`) + 86_400_000)
    .toISOString()
    .slice(0, 10);
  const employeeBase = `${base}/workforce/employees/${employee}`;
  const shift = randomUUID();
  expect(
    (
      await context.request.put(`${base}/workforce/shifts/${shift}`, {
        headers: headers(),
        data: {
          code: "NIGHT",
          name: "Night shift",
          startsAt: "22:00",
          endsAt: "06:00",
          breakMinutes: 30,
          timezone: "Asia/Jakarta",
          mode: "REMOTE",
          locationRequired: false,
          maxAccuracyMeters: 100,
          fence: null,
          reason: "Night coverage",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${employeeBase}/roster/${workDate}`, {
        headers: headers(),
        data: { shift: { id: shift, version: 0 }, reason: "Night coverage" },
      })
    ).status(),
  ).toBe(200);
  const clockIn = `${workDate}T15:00:00Z`,
    clockOut = `${workDate}T23:00:00Z`,
    device = randomUUID(),
    eventIds = [randomUUID(), randomUUID()];
  for (const [index, kind, capturedAt] of [
    [0, "CLOCK_IN", clockIn],
    [1, "CLOCK_OUT", clockOut],
  ] as const) {
    const captured = await request.post(`${employeeBase}/attendance`, {
      headers: { ...nativeHeaders, "Idempotency-Key": randomUUID() },
      data: {
        id: eventIds[index],
        workDate,
        kind,
        capturedAt,
        deviceId: device,
        offline: true,
        location: { latitude: -6.2, longitude: 106.8, accuracyMeters: 10, mocked: false },
      },
    });
    expect(captured.status()).toBe(200);
  }
  const evidencePath = `${employeeBase}/attendance?from=${workDate}&until=${workDate}`;
  const initial = (await (
    await request.get(evidencePath, { headers: nativeHeaders })
  ).json()) as AttendanceDayDto[];
  expect(initial[0]).toMatchObject({ pendingCount: 2, acceptedMinutes: 0 });
  const reviewRequests: { operation: string | undefined; body: unknown }[] = [];
  let lost = false;
  await page.route(`**/workforce/attendance/${eventIds[0]}/review`, async (route) => {
    reviewRequests.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    if (!lost) {
      lost = true;
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await response.dispose();
      return route.abort("failed");
    }
    return route.continue();
  });
  const dayPage = `/workforce/attendance/${employee}/days/${workDate}?company=${company}`;
  const exceptionsPage = `/workforce/attendance/exceptions?company=${company}&from=${workDate}&until=${workDate}`;
  const exceptionsApi = `${base}/workforce/attendance/exceptions?from=${workDate}&until=${workDate}`;
  expect((await request.get(exceptionsApi, { headers: nativeHeaders })).status()).toBe(403);
  await page.goto(exceptionsPage);
  const exceptions = page.getByRole("table", { name: "Employee days", exact: true });
  await expect(exceptions.getByRole("row")).toHaveCount(2);
  await expect(exceptions.getByRole("cell", { name: "2", exact: true })).toBeVisible();
  await expect(
    exceptions.getByRole("cell", {
      name: "Attendance Fixture Employee · ATTENDANCE-001",
      exact: true,
    }),
  ).toBeVisible();
  await expect
    .poll(() =>
      exceptions
        .getByRole("button", { name: /^View day: ATTENDANCE-001,/u })
        .evaluate((element) => {
          const bounds = element.getBoundingClientRect();
          const container = element.closest(".app-table-scroll")?.getBoundingClientRect();
          return (
            container !== undefined &&
            bounds.left >= container.left &&
            bounds.right <= container.right
          );
        }),
    )
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-exceptions-real.png",
    fullPage: true,
    animations: "disabled",
  });
  await exceptions.getByRole("button", { name: /^View day: ATTENDANCE-001,/u }).click();
  await expect(
    page.getByRole("heading", { name: "Attendance Fixture Employee", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("region", { name: "Clock-in / Pending review", exact: true })
    .getByRole("button", { name: "Review event", exact: true })
    .click();
  await page.getByLabel("Reason", { exact: true }).fill("Independent clock-in verification");
  await page.getByRole("button", { name: "Record decision", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Attendance updated.");
  expect(reviewRequests).toHaveLength(2);
  expect(reviewRequests[0]).toEqual(reviewRequests[1]);
  await page.goto(exceptionsPage);
  await expect(exceptions.getByRole("cell", { name: "1", exact: true })).toBeVisible();
  await expect(exceptions.getByRole("cell", { name: "Incomplete", exact: true })).toBeVisible();
  await exceptions.getByRole("button", { name: /^View day: ATTENDANCE-001,/u }).click();
  await page
    .getByRole("region", { name: "Clock-out / Pending review", exact: true })
    .getByRole("button", { name: "Review event", exact: true })
    .click();
  await page.getByLabel("Reason", { exact: true }).fill("Independent clock-out verification");
  await page.getByRole("button", { name: "Record decision", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Attendance updated.");
  const accepted = (await (
    await request.get(evidencePath, { headers: nativeHeaders })
  ).json()) as AttendanceDayDto[];
  expect(accepted[0]).toMatchObject({ pendingCount: 0, acceptedMinutes: 450, incomplete: false });
  await page.goto(exceptionsPage);
  await expect(
    page.getByText("No attendance exceptions match these filters.", { exact: true }),
  ).toBeVisible();
  await page.goto(dayPage);
  await page.getByRole("link", { name: "Correct day", exact: true }).click();
  await expect(page.getByLabel("Break (minutes)", { exact: true })).toHaveValue("30");
  await page.getByLabel("Clock-in", { exact: true }).fill(`${workDate}T22:15`);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed clock-in time");
  expect(
    (
      await context.request.post(`${employeeBase}/attendance/corrections`, {
        headers: headers(),
        data: {
          workDate,
          clockIn: null,
          clockOut: null,
          breakMinutes: 0,
          reason: "Competing verified absence",
        },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "Record correction", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(page.getByLabel("Record an absence", { exact: true })).toBeChecked();
  await page.getByLabel("Record an absence", { exact: true }).uncheck();
  await page.getByLabel("Clock-in", { exact: true }).fill(`${workDate}T22:15`);
  await page.getByLabel("Clock-out", { exact: true }).fill(`${clockOutDate}T06:00`);
  await page.getByLabel("Break (minutes)", { exact: true }).fill("30");
  await page.getByLabel("Reason", { exact: true }).fill("Final independent correction");
  await page.getByRole("button", { name: "Record correction", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Attendance updated.");
  const corrected = (await (
    await request.get(evidencePath, { headers: nativeHeaders })
  ).json()) as AttendanceDayDto[];
  expect(corrected[0]).toMatchObject({
    acceptedMinutes: 435,
    correction: { version: 1, reason: "Final independent correction" },
  });
  expect(corrected[0]?.entries.map((entry) => entry.capturedAt)).toContain(clockIn);
  const history = (await (
    await context.request.get(`${employeeBase}/attendance/corrections?workDate=${workDate}`)
  ).json()) as { items: AttendanceCorrectionDto[] };
  expect(history.items).toHaveLength(2);
  const latest = history.items[0],
    original = history.items[1];
  if (!latest || !original) throw new Error("Missing committed corrections");
  await page.getByRole("link", { name: "View attendance", exact: true }).click();
  await page.getByRole("link", { name: "Correction history", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Employee and work date", exact: true }),
  ).toContainText("Attendance Fixture Employee");
  const table = page.getByRole("table", { name: "Corrections", exact: true });
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: "View correction: 0", exact: true }).click();
  const details = page.getByRole("dialog", { name: "Correction details", exact: true });
  await expect(details).toContainText(original.id);
  await expect(details).toContainText("Competing verified absence");
  await expect(details.getByText("Absence", { exact: true })).toBeVisible();
  await details.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "View correction: 1", exact: true }).click();
  await expect(details).toContainText(latest.id);
  await expect(details).toContainText(latest.actorId);
  await expect(details).toContainText("Final independent correction");
  await page.screenshot({
    path: "../../.work/dashboard-attendance-history-real.png",
    fullPage: true,
    animations: "disabled",
  });
  await details.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("link", { name: "Back to attendance day", exact: true }).click();
  await expect(page.getByText("435", { exact: true })).toBeVisible();
  await expect(page.getByText("Final independent correction", { exact: true })).toBeVisible();

  const lateId = randomUUID();
  const late = await request.post(`${employeeBase}/attendance`, {
    headers: { ...nativeHeaders, "Idempotency-Key": randomUUID() },
    data: {
      id: lateId,
      workDate,
      kind: "CLOCK_IN",
      capturedAt: clockIn,
      deviceId: device,
      offline: true,
    },
  });
  expect(late.status()).toBe(200);
  const correctedQueue = (await (
    await context.request.get(exceptionsApi)
  ).json()) as AttendanceExceptionPageDto;
  expect(correctedQueue.items).toHaveLength(1);
  expect(correctedQueue.items[0]).toMatchObject({
    employeeId: employee,
    pendingCount: 1,
    incomplete: false,
    correctionVersion: 1,
  });
  await page.goto(`${exceptionsPage}&kind=PENDING`);
  await expect(exceptions.getByRole("cell", { name: "Corrected", exact: true })).toBeVisible();
  const rejected = await context.request.post(`${base}/workforce/attendance/${lateId}/review`, {
    headers: headers(),
    data: {
      version: 0,
      decision: "REJECT",
      reason: "Late duplicate evidence after verified correction",
    },
  });
  expect(rejected.status()).toBe(200);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByText("No attendance exceptions match these filters.", { exact: true }),
  ).toBeVisible();
  const finalDay = (await (
    await request.get(evidencePath, { headers: nativeHeaders })
  ).json()) as AttendanceDayDto[];
  expect(finalDay[0]).toMatchObject({
    acceptedMinutes: 435,
    pendingCount: 0,
    correction: { version: 1 },
  });
  expect(finalDay[0]?.entries).toHaveLength(3);
});
