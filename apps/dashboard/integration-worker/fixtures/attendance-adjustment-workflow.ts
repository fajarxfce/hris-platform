import { randomUUID } from "node:crypto";
import { type APIRequestContext, type BrowserContext, expect, type Page } from "@playwright/test";
import { attendanceAdjustmentDetailsDto } from "../../src/features/workforce/data/models/attendance-adjustment-dto";
import { attendanceDaysDto } from "../../src/features/workforce/data/models/attendance-dto";
import type { WorkPeriodSnapshotDto } from "../../src/features/workforce/data/models/work-period-snapshot-dto";
import { recordClosedAttendanceAdjustment } from "./attendance-adjustments";

/** Review late captures in the browser, then recover an immutable revision after a newer correction. */
export async function exerciseAttendanceAdjustments(input: {
  page: Page;
  admin: BrowserContext;
  request: APIRequestContext;
  company: string;
  employee: string;
  workDate: string;
  original: WorkPeriodSnapshotDto;
  nativeHeaders: Record<string, string>;
  headers: () => Record<string, string>;
}) {
  const { page, admin, request, company, employee, workDate, original, nativeHeaders, headers } =
    input;
  const month = workDate.slice(0, 7),
    base = `/api/v1/companies/${company}`,
    employeeBase = `${base}/workforce/employees/${employee}`;
  const adjustmentBase = `${employeeBase}/attendance/adjustments`;
  const nextDate = new Date(Date.parse(`${workDate}T00:00:00Z`) + 86_400_000)
    .toISOString()
    .slice(0, 10);
  for (const [kind, capturedAt] of [
    ["CLOCK_IN", `${workDate}T23:00:00+07:00`],
    ["CLOCK_OUT", `${nextDate}T05:00:00+07:00`],
  ] as const) {
    const capture = await request.post(`${employeeBase}/attendance`, {
      headers: { ...nativeHeaders, "Idempotency-Key": randomUUID() },
      data: {
        id: randomUUID(),
        workDate,
        kind,
        capturedAt: new Date(capturedAt).toISOString(),
        deviceId: randomUUID(),
        offline: true,
      },
    });
    expect(capture.status()).toBe(200);
  }
  const saved: { operation: string; body: unknown }[] = [];
  await page.route(`**${adjustmentBase}`, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    saved.push({
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON(),
    });
    if (saved.length > 1) return route.continue();
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    await response.dispose();
    return route.abort("failed");
  });
  await page.getByRole("button", { name: `Review day: ${workDate}`, exact: true }).click();
  const table = page.getByRole("table", { name: "Current attendance", exact: true });
  await expect(table).toContainText("Original closing");
  await expect(table).toContainText("Unrecorded");
  await page.getByRole("combobox", { name: /^Decision · Clock-in/ }).selectOption("ACCEPT");
  await page.getByRole("combobox", { name: /^Decision · Clock-out/ }).selectOption("REJECT");
  const correction = page.getByRole("region", { name: "Correct attendance", exact: true });
  await correction
    .getByRole("textbox", { name: "Clock-in", exact: true })
    .fill(`${workDate}T23:00`);
  await correction
    .getByRole("textbox", { name: "Clock-out", exact: true })
    .fill(`${nextDate}T05:00`);
  await page.getByRole("spinbutton", { name: "Break (minutes)", exact: true }).fill("60");
  await page
    .getByRole("textbox", { name: "Reason", exact: true })
    .fill("Reviewed overnight work after closing");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original adjustment", exact: true });
  await expect(retry).toBeVisible();
  expect(saved[0]?.body).toMatchObject({
    workDate,
    clockIn: `${workDate}T16:00:00Z`,
    clockOut: `${workDate}T22:00:00Z`,
    breakMinutes: 60,
    decisions: expect.arrayContaining([
      { eventId: expect.any(String), expectedVersion: 0, decision: "ACCEPT" },
      { eventId: expect.any(String), expectedVersion: 0, decision: "REJECT" },
    ]),
  });
  const later = await recordClosedAttendanceAdjustment({
    operator: admin,
    company,
    employee,
    workDate,
    absence: true,
    headers,
  });
  expect(later.version).toBe(2);
  await retry.click();
  await expect(
    page.getByRole("link", { name: "View recorded revision", exact: true }),
  ).toBeVisible();
  expect(saved).toHaveLength(2);
  expect(saved[1]).toEqual(saved[0]);
  await page.getByRole("link", { name: "View recorded revision", exact: true }).click();
  const retained = page.getByRole("table", { name: "Retained monthly attendance", exact: true });
  await expect(retained).toContainText("300");
  const id = new URL(page.url()).pathname.split("/").at(-1);
  const response = await admin.request.get(`${adjustmentBase}/${id}`);
  expect(response.status()).toBe(200);
  const first = attendanceAdjustmentDetailsDto.parse(await response.json());
  expect(first.summary.revision).toBe(1);
  expect(first.snapshot.days.find((day) => day.workDate === workDate)).toMatchObject({
    fact: "WORKED",
    acceptedMinutes: 300,
  });
  await page.screenshot({
    path: "../../.work/dashboard-attendance-adjustment-real.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Back to history", exact: true }).click();
  const history = page.getByRole("table", { name: "Adjustment history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(3);
  await history
    .getByRole("button", { name: `View revision: 2 · ${workDate}`, exact: true })
    .click();
  await expect(retained).toContainText("Recorded absence");
  const latest = attendanceAdjustmentDetailsDto.parse(
    await (await admin.request.get(`${adjustmentBase}/${later.id}`)).json(),
  );
  expect(latest.snapshot.days.find((day) => day.workDate === workDate)).toMatchObject({
    fact: "ABSENCE_RECORDED",
    acceptedMinutes: 0,
  });
  expect(
    await (
      await admin.request.get(`${base}/workforce/periods/${month}/employees/${employee}`)
    ).json(),
  ).toEqual(original);
  const current = await request.get(
    `${employeeBase}/attendance?from=${workDate}&until=${workDate}`,
    { headers: nativeHeaders },
  );
  expect(current.status()).toBe(200);
  const day = attendanceDaysDto.parse(await current.json())[0];
  expect(day).toMatchObject({
    pendingCount: 0,
    acceptedMinutes: 0,
    correction: { clockIn: null, clockOut: null, reason: "Reviewed absence after closing" },
  });
  expect(
    day?.entries
      .filter((entry) => entry.review?.reason === "Reviewed overnight work after closing")
      .map((entry) => entry.status)
      .sort(),
  ).toEqual(["ACCEPTED", "REJECTED"]);
}
