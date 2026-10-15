import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import type {
  LeaveBatchDetailsDto,
  LeaveBatchDto,
} from "../src/features/leave/data/models/leave-batch-dto";
import type { LeaveLedgerDto } from "../src/features/leave/data/models/leave-ledger-dto";

test("reviewed accrual and year closing preserve one posting across response loss through the real worker", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-leave-batches-admin@example.invalid",
    "LEAVEBATCH",
    "Leave Processing Company",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const base = `/api/v1/companies/${company}`,
    headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const dateResponse = await context.request.get(
    `${base}/workforce/periods/${new Date().toISOString().slice(0, 7)}`,
  );
  expect(dateResponse.status()).toBe(200);
  const today = ((await dateResponse.json()) as { companyDate: string }).companyDate;
  await dateResponse.dispose();
  const year = Number(today.slice(0, 4)) - 1,
    period = `${year}-12`,
    firstDay = `${year - 1}-01-01`,
    eligible = randomUUID(),
    excluded = randomUUID(),
    type = randomUUID();
  for (const employee of [
    { id: eligible, name: "Eligible Employee", contract: "PERMANENT" },
    { id: excluded, name: "Excluded Employee", contract: "FIXED_TERM" },
  ]) {
    const response = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: employee.id,
        employeeNumber: employee.contract === "PERMANENT" ? "BATCH-001" : "BATCH-002",
        person: { id: randomUUID(), legalName: employee.name, nationality: "ID" },
        terms: {
          effectiveFrom: firstDay,
          startDate: firstDay,
          contract: employee.contract,
          endDate: employee.contract === "FIXED_TERM" ? `${year + 1}-12-31` : null,
          status: "ACTIVE",
        },
        reason: "Leave batch fixture",
      },
    });
    expect(response.status()).toBe(200);
    await response.dispose();
  }
  const policy = await context.request.put(`${base}/leave/types/${type}`, {
    headers: headers(),
    data: {
      code: "BATCHLEAVE",
      name: "Batch annual leave",
      effectiveFrom: firstDay,
      paid: true,
      allowPartialDays: true,
      minServiceMonths: 0,
      allowedContracts: ["PERMANENT"],
      maxRequestDays: 12,
      active: true,
      attachmentRequired: false,
      accrual: { frequency: "MONTHLY", daysPerPeriod: "1", carryLimitDays: "5" },
      reason: "Reviewed entitlement fixture",
    },
  });
  expect(policy.status()).toBe(200);
  await policy.dispose();
  const writes: { operation: string; body: unknown }[] = [];
  let lose = true;
  await page.route(`**${base}/leave/accrual-batches`, async (route) => {
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
  await page.goto(`/leave/batches/new?company=${company}&kind=ACCRUAL`);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose leave type", exact: true })
    .getByRole("button", { name: "Select: Batch annual leave", exact: true })
    .click();
  await page.getByLabel("Accrual month", { exact: true }).fill(period);
  await page.getByRole("button", { name: "Review selection", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review selection", exact: true })).toContainText(
    "Batch annual leave",
  );
  await page.getByLabel("Reason", { exact: true }).fill("December entitlement reviewed");
  await page.getByRole("button", { name: "Post accrual", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Process submitted.");
  expect(writes).toHaveLength(2);
  expect(writes[0]).toEqual(writes[1]);
  const input = writes[0]?.body as { id: string; employeeIds: string[] };
  expect([...input.employeeIds].sort()).toEqual([eligible, excluded].sort());
  await expect
    .poll(
      async () => {
        const response = await context.request.get(`${base}/leave/batches/${input.id}`);
        try {
          expect(response.status()).toBe(200);
          return ((await response.json()) as LeaveBatchDetailsDto).job.status;
        } finally {
          await response.dispose();
        }
      },
      { timeout: 30_000 },
    )
    .toBe("SUCCEEDED");
  const response = await context.request.get(`${base}/leave/batches/${input.id}`);
  expect(response.status()).toBe(200);
  const details = (await response.json()) as LeaveBatchDetailsDto;
  await response.dispose();
  expect(details.counts).toEqual({ applied: 1, unchanged: 0, skipped: 1, failed: 0, completed: 2 });
  expect(details.batch.totalEmployees).toBe(2);
  expect(details.results.items.find((item) => item.employeeId === excluded)).toMatchObject({
    status: "SKIPPED",
    failureCode: "leave_employee_ineligible",
  });
  expect(details.employees.map((employee) => employee.name).sort()).toEqual([
    "Eligible Employee",
    "Excluded Employee",
  ]);
  await page.getByRole("link", { name: "View process", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toContainText(
    "Eligible Employee",
  );
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toContainText(
    "Skipped",
  );
  await expect(page.getByRole("region", { name: "Process details", exact: true })).toContainText(
    "2 / 2",
  );
  await page.screenshot({
    path: "/tmp/hris-leave-batches/outcomes-real-worker.png",
    fullPage: true,
  });
  const ledgerResponse = await context.request.get(
    `${base}/leave/employees/${eligible}/balances/${type}/${year}`,
  );
  expect(ledgerResponse.status()).toBe(200);
  const ledger = (await ledgerResponse.json()) as LeaveLedgerDto;
  await ledgerResponse.dispose();
  expect(ledger.balance.availableDays).toBe("1");
  expect(ledger.entries.items).toHaveLength(1);
  expect(ledger.entries.items[0]?.kind).toBe("GRANT");
  const listResponse = await context.request.get(`${base}/leave/batches`);
  expect(listResponse.status()).toBe(200);
  expect(((await listResponse.json()) as { items: LeaveBatchDto[] }).items).toHaveLength(1);
  await listResponse.dispose();
  await page.goto(`/leave/batches/new?company=${company}&kind=YEAR_CLOSE`);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose leave type", exact: true })
    .getByRole("button", { name: "Select: Batch annual leave", exact: true })
    .click();
  await page.getByLabel("Leave year", { exact: true }).fill(String(year));
  await page.getByRole("button", { name: "Review selection", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Year-end carryover reviewed");
  const posted = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith("/leave/year-close-batches"),
  );
  await page.getByRole("button", { name: "Close leave year", exact: true }).click();
  const closingResponse = await posted;
  expect(closingResponse.status()).toBe(200);
  const closingId = ((await closingResponse.json()) as { id: string }).id;
  await expect(page.getByRole("status")).toContainText("Process submitted.");
  await expect
    .poll(
      async () => {
        const result = await context.request.get(`${base}/leave/batches/${closingId}`);
        try {
          expect(result.status()).toBe(200);
          return ((await result.json()) as LeaveBatchDetailsDto).job.status;
        } finally {
          await result.dispose();
        }
      },
      { timeout: 30_000 },
    )
    .toBe("SUCCEEDED");
  await page.getByRole("link", { name: "View process", exact: true }).click();
  await expect(page.getByRole("region", { name: "Process details", exact: true })).toContainText(
    "Completed",
  );
  const sourceResponse = await context.request.get(
      `${base}/leave/employees/${eligible}/balances/${type}/${year}`,
    ),
    destinationResponse = await context.request.get(
      `${base}/leave/employees/${eligible}/balances/${type}/${year + 1}`,
    );
  expect(sourceResponse.status()).toBe(200);
  expect(destinationResponse.status()).toBe(200);
  const source = (await sourceResponse.json()) as LeaveLedgerDto,
    destination = (await destinationResponse.json()) as LeaveLedgerDto;
  await sourceResponse.dispose();
  await destinationResponse.dispose();
  expect(source.balance).toMatchObject({ availableDays: "0", closed: true });
  expect(destination.balance).toMatchObject({ availableDays: "1", closed: false });
  expect(source.entries.items.filter((entry) => entry.kind === "GRANT")).toHaveLength(1);
  expect(source.entries.items.filter((entry) => entry.kind === "CARRY_OUT")).toHaveLength(1);
  expect(destination.entries.items.filter((entry) => entry.kind === "CARRY_IN")).toHaveLength(1);
});
