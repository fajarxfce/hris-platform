import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import type { JobDto } from "../src/features/jobs/data/models/job-dto";
import type {
  LeaveAccrualScheduleDetailsDto,
  LeaveAccrualScheduleReviewDto,
} from "../src/features/leave/data/models/leave-accrual-schedule-dto";
import type { LeaveBatchDetailsDto } from "../src/features/leave/data/models/leave-batch-dto";
import type { LeaveLedgerDto } from "../src/features/leave/data/models/leave-ledger-dto";

test("reviewed periods survive response loss and future cancellation keeps its due time through recovery", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-leave-schedule-admin@example.invalid",
    "LEAVESCHEDULE",
    "Leave Schedule Company",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const base = `/api/v1/companies/${company}`,
    headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const clock = await context.request.get(
    `${base}/workforce/periods/${new Date().toISOString().slice(0, 7)}`,
  );
  expect(clock.status()).toBe(200);
  const today = ((await clock.json()) as { companyDate: string }).companyDate;
  await clock.dispose();
  const month = new Date(`${today.slice(0, 7)}-01T00:00:00Z`);
  month.setUTCMonth(month.getUTCMonth() - 1);
  const first = month.toISOString().slice(0, 7),
    employmentStart = `${Number(first.slice(0, 4)) - 1}-01-01`,
    eligible = randomUUID(),
    excluded = randomUUID(),
    type = randomUUID();
  for (const employee of [
    { id: eligible, name: "Eligible Schedule Employee", contract: "PERMANENT" },
    { id: excluded, name: "Excluded Schedule Employee", contract: "FIXED_TERM" },
  ]) {
    const response = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: employee.id,
        employeeNumber: employee.contract === "PERMANENT" ? "SCHED-001" : "SCHED-002",
        person: { id: randomUUID(), legalName: employee.name, nationality: "ID" },
        terms: {
          effectiveFrom: employmentStart,
          startDate: employmentStart,
          contract: employee.contract,
          endDate:
            employee.contract === "FIXED_TERM" ? `${Number(today.slice(0, 4)) + 2}-12-31` : null,
          status: "ACTIVE",
        },
        reason: "Reviewed schedule fixture",
      },
    });
    expect(response.status()).toBe(200);
    await response.dispose();
  }
  const policy = await context.request.put(`${base}/leave/types/${type}`, {
    headers: headers(),
    data: {
      code: "SCHEDULELEAVE",
      name: "Scheduled annual leave",
      effectiveFrom: employmentStart,
      paid: true,
      allowPartialDays: true,
      minServiceMonths: 0,
      allowedContracts: ["PERMANENT"],
      maxRequestDays: 12,
      active: true,
      attachmentRequired: false,
      accrual: { frequency: "MONTHLY", daysPerPeriod: "1", carryLimitDays: "5" },
      reason: "Schedule policy fixture",
    },
  });
  expect(policy.status()).toBe(200);
  await policy.dispose();
  const reviewedResponse = await context.request.get(
    `${base}/leave/accrual-schedules/review?${new URLSearchParams({ typeId: type, firstPeriod: first, periods: "3" })}`,
  );
  expect(reviewedResponse.status()).toBe(200);
  const reviewed = (await reviewedResponse.json()) as LeaveAccrualScheduleReviewDto;
  await reviewedResponse.dispose();
  expect(reviewed.periods).toHaveLength(3);
  expect([...reviewed.employeeIds].sort()).toEqual([eligible, excluded].sort());
  const writes: { operation: string; body: unknown }[] = [];
  let lose = true;
  await page.route(`**${base}/leave/accrual-schedules`, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    writes.push({
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON() as unknown,
    });
    if (!lose) return route.continue();
    lose = false;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    await response.dispose();
    return route.abort("connectionfailed");
  });
  await page.goto(`/leave/accrual-schedules/new?company=${company}`);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose leave type", exact: true })
    .getByRole("button", { name: "Select: Scheduled annual leave", exact: true })
    .click();
  await page.getByLabel("First period", { exact: true }).fill(first);
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Periods", exact: true }).getByRole("row"),
  ).toHaveCount(4);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed finite entitlement schedule");
  await page.getByRole("button", { name: "Schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Accrual schedule created.");
  expect(writes).toHaveLength(2);
  expect(writes[0]).toEqual(writes[1]);
  const command = writes[0]?.body as { id: string; employeeIds: string[] };
  expect([...command.employeeIds].sort()).toEqual([eligible, excluded].sort());
  async function readSchedule() {
    const response = await context.request.get(`${base}/leave/accrual-schedules/${command.id}`);
    try {
      expect(response.status()).toBe(200);
      return (await response.json()) as LeaveAccrualScheduleDetailsDto;
    } finally {
      await response.dispose();
    }
  }
  async function readBatch(id: string) {
    const response = await context.request.get(`${base}/leave/batches/${id}`);
    try {
      expect(response.status()).toBe(200);
      return (await response.json()) as LeaveBatchDetailsDto;
    } finally {
      await response.dispose();
    }
  }
  const initial = await readSchedule(),
    past = initial.periods[0],
    future = initial.periods[2];
  if (!past || !future) throw Error("Expected three scheduled periods");
  expect(initial.schedule).toMatchObject({
    id: command.id,
    periodCount: 3,
    totalEmployees: 2,
    firstPeriod: first,
  });
  expect(future.scheduledFor).toBe(reviewed.periods[2]?.dueAt);
  expect(Date.parse(future.scheduledFor)).toBeGreaterThan(Date.now() + 86400000);
  await expect
    .poll(async () => (await readBatch(past.batch.id)).job.status, { timeout: 30_000 })
    .toBe("SUCCEEDED");
  const completed = await readBatch(past.batch.id);
  expect(completed.counts).toEqual({
    applied: 1,
    unchanged: 0,
    skipped: 1,
    failed: 0,
    completed: 2,
  });
  const applied = completed.results.items.find((item) => item.employeeId === eligible);
  expect(applied?.status).toBe("APPLIED");
  expect(completed.results.items.find((item) => item.employeeId === excluded)).toMatchObject({
    status: "SKIPPED",
    failureCode: "leave_employee_ineligible",
  });
  const ledgerResponse = await context.request.get(
    `${base}/leave/employees/${eligible}/balances/${type}/${first.slice(0, 4)}`,
  );
  expect(ledgerResponse.status()).toBe(200);
  const ledger = (await ledgerResponse.json()) as LeaveLedgerDto;
  await ledgerResponse.dispose();
  expect(
    ledger.entries.items.filter(
      (item) => item.kind === "GRANT" && item.sourceId === applied?.resourceId,
    ),
  ).toHaveLength(1);
  const history = await context.request.get(`${base}/leave/accrual-schedules`);
  expect(history.status()).toBe(200);
  expect(((await history.json()) as { items: unknown[] }).items).toHaveLength(1);
  await history.dispose();
  const progress = await readSchedule();
  expect(progress.periods[0]).toMatchObject({ jobStatus: "SUCCEEDED", completedEmployees: 2 });
  expect(progress.periods[2]).toMatchObject({ jobStatus: "QUEUED", completedEmployees: 0 });
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("table", { name: "Periods", exact: true })).toContainText(
    "Completed",
  );
  await page.screenshot({
    path: "/tmp/hris-leave-accrual/schedule-real-worker.png",
    fullPage: true,
  });
  const futureLabel = new Intl.DateTimeFormat("en", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${future.batch.period}-01T00:00:00Z`));
  await page.getByRole("button", { name: `View progress: ${futureLabel}`, exact: true }).click();
  await expect(page.getByRole("region", { name: "Process details", exact: true })).toContainText(
    "Queued",
  );
  await page.getByRole("link", { name: "View job", exact: true }).click();
  const job = page.getByRole("dialog", { name: "Job details", exact: true });
  await job.getByRole("button", { name: "Request cancellation", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Cancel this job?", exact: true })
    .getByRole("button", { name: "Request cancellation", exact: true })
    .click();
  await expect
    .poll(async () => (await readBatch(future.batch.id)).job.status, { timeout: 30_000 })
    .toBe("CANCELLED");
  const stopped = await readBatch(future.batch.id);
  expect(stopped.batch.status).toBe("STOPPED");
  expect(stopped.counts.completed).toBe(0);
  await page.goto(
    `/leave/batches/${future.batch.id}?${new URLSearchParams({ company, schedule: command.id })}`,
  );
  await page.getByRole("link", { name: "Resume process", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed recovery of future period");
  await page.getByRole("button", { name: "Resume process", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Process resumed.");
  const resumed = await readBatch(future.batch.id);
  expect(resumed.batch.jobId).not.toBe(future.batch.jobId);
  expect(resumed.counts.completed).toBe(0);
  expect(resumed.attempts).toHaveLength(2);
  expect(resumed.job.status).toBe("QUEUED");
  const recoveredJob = await context.request.get(`${base}/jobs/${resumed.batch.jobId}`);
  expect(recoveredJob.status()).toBe(200);
  const queued = (await recoveredJob.json()) as JobDto;
  await recoveredJob.dispose();
  expect(queued.scheduledFor).toBe(future.scheduledFor);
  expect(Date.parse(queued.availableAt)).toBeGreaterThanOrEqual(Date.parse(future.scheduledFor));
  await page.getByRole("link", { name: "View process", exact: true }).click();
  await page.getByRole("link", { name: "Back to schedule", exact: true }).click();
  await expect(page.getByRole("table", { name: "Periods", exact: true })).toContainText("Queued");
  const retained = await readSchedule();
  expect(retained.periods[2]?.batch.jobId).toBe(resumed.batch.jobId);
  expect(retained.periods[2]?.scheduledFor).toBe(future.scheduledFor);
  expect(retained.schedule).toEqual(initial.schedule);
  const replacement = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: {
      code: "LEAVEREPLACEMENT",
      name: "Schedule Replacement Company",
      timezone: "Asia/Jakarta",
    },
  });
  expect(replacement.status()).toBe(200);
  const other = ((await replacement.json()) as { id: string }).id;
  await replacement.dispose();
  // Refresh admitted company metadata, then replace the workspace through the actual selector.
  await page.reload();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(other);
  await expect(page.getByRole("status")).toContainText("No accrual schedules.");
  await expect(page.getByRole("main")).not.toContainText("Reviewed finite entitlement schedule");
  expect(page.url()).not.toContain(command.id);
});
