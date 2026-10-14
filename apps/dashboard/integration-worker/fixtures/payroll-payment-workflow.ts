import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { type BrowserContext, expect, type Page, type Route } from "@playwright/test";
import { companyDateTimeInput } from "../../src/core/presentation/dates/company-date-time";
import { payrollCalculationMinor } from "../../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import { payrollPayablePageDto } from "../../src/features/payroll/data/models/payroll-payable-dto";
import { payrollPaymentReviewDto } from "../../src/features/payroll/data/models/payroll-payment-dto";
import {
  type PayrollPayslipSummaryDto,
  payrollPaymentProgressDto,
} from "../../src/features/payroll/data/models/payroll-payslip-dto";

/** Borrow the caller's enrolled accounts; all payment records belong to its isolated company. */
export async function exercisePayrollPayments(input: {
  maker: Page;
  checker: Page;
  admin: BrowserContext;
  company: string;
  month: string;
  summary: PayrollPayslipSummaryDto;
  headers: () => Record<string, string>;
}) {
  const { maker, checker, admin, company, month, summary, headers } = input,
    base = `/api/v1/companies/${company}`,
    payments = `${base}/payroll/payments`,
    makerId = "a0000000-0000-4000-8000-000000000053",
    checkerId = "a0000000-0000-4000-8000-000000000054",
    from = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10),
    until = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10),
    query = new URLSearchParams({ company, from, until }).toString();
  for (const [account, expectedVersion] of [
    [makerId, 2],
    [checkerId, 1],
  ] as const) {
    const grant = await admin.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: {
        active: true,
        expectedVersion,
        permissions: ["company.read", "payroll.pay"],
        reason: "Independent payroll finance acceptance",
      },
    });
    expect(grant.status()).toBe(200);
  }
  const payablesResponse = await maker.request.get(
    `${payments}/payables?from=${from}&until=${until}&limit=20`,
  );
  expect(payablesResponse.status()).toBe(200);
  const payables = payrollPayablePageDto.parse(await payablesResponse.json());
  expect(payables.items).toHaveLength(1);
  expect(payables.items[0]).toMatchObject({
    assessmentId: summary.id,
    employmentId: summary.employeeId,
    taxMonth: month,
  });
  expect(payrollCalculationMinor(payables.items[0]?.amount ?? "")).toBe(
    payrollCalculationMinor(summary.takeHome),
  );
  for (const actor of [maker, checker])
    expect((await actor.request.get(`${base}/payroll/payslips/${summary.id}`)).status()).toBe(403);

  type CapturedCommand = { path: string; operation: string; body: Record<string, unknown> };
  const preparations: CapturedCommand[] = [],
    commands = { release: [] as CapturedCommand[], reconcile: [] as CapturedCommand[] },
    dropped = new Set<string>(),
    preparePattern = `**${payments}/*`,
    commandPattern = `**${payments}/*/*`;
  const onPrepare = async (route: Route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.continue();
    preparations.push({
      path: new URL(request.url()).pathname,
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON() as Record<string, unknown>,
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (!dropped.has("prepare")) {
      dropped.add("prepare");
      return route.abort("failed");
    }
    return route.fulfill({ response });
  };
  const onCommand = async (route: Route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname,
      action = path.split("/").at(-1);
    if (request.method() !== "POST" || (action !== "release" && action !== "reconcile"))
      return route.continue();
    commands[action].push({
      path,
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON() as Record<string, unknown>,
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (!dropped.has(action)) {
      dropped.add(action);
      return route.abort("failed");
    }
    return route.fulfill({ response });
  };
  try {
    await maker.route(preparePattern, onPrepare);
    await checker.route(commandPattern, onCommand);
    await maker.goto(`/payroll/payments/new?${query}`);
    await maker.getByLabel("Batch title", { exact: true }).fill(`Payroll ${month}`);
    await maker
      .getByLabel("Preparation note", { exact: true })
      .fill("Verified payroll bank details");
    await maker.getByRole("button", { name: "Add payslips", exact: true }).click();
    const picker = maker.getByRole("dialog", { name: "Select payable salaries", exact: true });
    await picker
      .getByRole("button", {
        name: `Add payslip: ${month} · ${summary.employeeName}`,
        exact: true,
      })
      .click();
    await picker.getByRole("button", { name: "Done", exact: true }).click();
    await maker
      .getByRole("button", {
        name: `Edit bank details: ${month} · ${summary.employeeName}`,
        exact: true,
      })
      .click();
    const destination = maker.getByRole("dialog", { name: "Bank details", exact: true });
    await destination.getByLabel("Bank code", { exact: true }).fill("014");
    await destination.getByLabel("Account number", { exact: true }).fill("001234567890");
    await destination.getByLabel("Account name", { exact: true }).fill(summary.employeeName);
    await destination.getByRole("button", { name: "Save bank details", exact: true }).click();
    await maker.getByRole("button", { name: "Review instructions", exact: true }).click();
    await expect(
      maker.getByRole("table", { name: "Bank instructions", exact: true }),
    ).toContainText("001234567890");
    await maker.getByRole("button", { name: "Create batch", exact: true }).click();
    await maker.getByRole("button", { name: "Retry original preparation", exact: true }).click();
    await expect(maker.getByRole("status")).toContainText("Payment batch prepared.");
    expect(preparations).toHaveLength(2);
    expect(preparations[1]).toEqual(preparations[0]);
    expect(preparations[0]?.operation).toMatch(/^[0-9a-f-]{36}$/u);
    const batchId = preparations[0]?.path.split("/").at(-1);
    if (!batchId) throw new Error("Prepared payment identifier required");
    const batchPath = `${payments}/${batchId}`,
      detailPath = `/payroll/payments/${batchId}?${query}`;
    const preparedResponse = await maker.request.get(`${batchPath}/review?historyLimit=20`);
    expect(preparedResponse.status()).toBe(200);
    const prepared = payrollPaymentReviewDto.parse(await preparedResponse.json());
    expect(prepared.batch).toMatchObject({ status: "PREPARED", version: 0, createdBy: makerId });
    expect(prepared.batch.items).toHaveLength(1);
    expect(prepared.batch.items[0]).toMatchObject({
      assessmentId: summary.id,
      taxMonth: month,
      status: "PREPARED",
      destination: {
        bankCode: "014",
        accountNumber: "001234567890",
        accountName: summary.employeeName,
      },
    });
    expect(payrollCalculationMinor(prepared.batch.totalAmount)).toBe(
      payrollCalculationMinor(summary.takeHome),
    );
    expect(prepared.history.items.map((action) => action.kind)).toEqual(["PREPARED"]);
    expect(prepared.availableActions).toEqual(["CANCEL"]);
    await maker.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await expect(maker.getByRole("button", { name: "Release batch", exact: true })).toHaveCount(0);
    await expect(maker.getByRole("button", { name: "Cancel batch", exact: true })).toBeVisible();
    const reserved = await maker.request.get(
      `${payments}/payables?from=${from}&until=${until}&limit=20`,
    );
    expect(reserved.status()).toBe(200);
    expect(payrollPayablePageDto.parse(await reserved.json()).items).toEqual([]);

    await checker.goto(detailPath);
    await checker.getByRole("button", { name: "Release batch", exact: true }).click();
    await expect(
      checker.getByRole("table", { name: "Payment instructions", exact: true }),
    ).toContainText(month);
    await checker.getByLabel("Reason", { exact: true }).fill("Independent bank instruction review");
    await checker.getByRole("button", { name: "Release batch", exact: true }).click();
    await checker.getByRole("button", { name: "Retry original action", exact: true }).click();
    await expect(checker.getByRole("status")).toContainText("Payment batch updated.");
    expect(commands.release).toHaveLength(2);
    expect(commands.release[1]).toEqual(commands.release[0]);
    expect(commands.release[0]?.body.expectedVersion).toBe(0);
    await checker.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await checker.getByRole("tab", { name: "Bank instructions", exact: true }).click();
    await expect(checker.getByRole("button", { name: "Download CSV", exact: true })).toBeVisible();
    const downloadReady = checker.waitForEvent("download");
    await checker.getByRole("button", { name: "Download CSV", exact: true }).click();
    const download = await downloadReady;
    expect(download.suggestedFilename()).toBe(`payroll-payments-${batchId}.csv`);
    const downloadPath = await download.path();
    if (!downloadPath) throw new Error("Payment worksheet download required");
    const worksheet = await readFile(downloadPath, "utf8");
    expect(worksheet).toContain("'001234567890");
    expect(worksheet).toContain(summary.id);
    expect(worksheet.trimEnd().split(/\r?\n/u)).toHaveLength(2);
    expect(Buffer.byteLength(worksheet)).toBeLessThanOrEqual(256 * 1024);
    const releasedResponse = await checker.request.get(`${batchPath}/review?historyLimit=20`);
    expect(releasedResponse.status()).toBe(200);
    const released = payrollPaymentReviewDto.parse(await releasedResponse.json());
    expect(released.batch).toMatchObject({
      status: "RELEASED",
      version: 1,
      releasedBy: checkerId,
    });
    expect(released.batch.items[0]?.status).toBe("PENDING");
    expect(released.availableActions).toEqual(["RECONCILE", "EXPORT"]);
    await checker.screenshot({
      path: "../../.work/dashboard-payroll-payment-real-released.png",
      fullPage: true,
      animations: "disabled",
    });

    await checker.getByRole("button", { name: "Reconcile payments", exact: true }).click();
    await checker
      .getByRole("button", {
        name: `Edit result: ${summary.employeeName} · ${summary.employeeNumber} · ${month}`,
        exact: true,
      })
      .click();
    const outcome = checker.getByRole("dialog", { name: "Bank result", exact: true }),
      reference = `BROWSER-${randomUUID()}`;
    await outcome
      .getByRole("combobox", { name: "Bank result", exact: true })
      .selectOption("SUCCEEDED");
    await outcome.getByLabel("Bank transaction reference", { exact: true }).fill(reference);
    await outcome
      .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
      .fill(companyDateTimeInput(new Date().toISOString(), "Asia/Jakarta").replace(/:00$/u, ""));
    await outcome
      .getByLabel("Result note", { exact: true })
      .fill("Confirmed on fixture bank statement");
    await outcome.getByRole("button", { name: "Add to reconciliation", exact: true }).click();
    await expect(outcome).toHaveCount(0);
    await checker
      .getByLabel("Reconciliation note", { exact: true })
      .fill("Salary payment confirmed");
    await checker.getByRole("button", { name: "Submit results", exact: true }).click();
    await checker
      .getByRole("button", { name: "Retry original reconciliation", exact: true })
      .click();
    await expect(checker.getByRole("status")).toContainText("Payment results recorded.");
    expect(commands.reconcile).toHaveLength(2);
    expect(commands.reconcile[1]).toEqual(commands.reconcile[0]);
    expect(commands.reconcile[0]?.body.expectedVersion).toBe(1);
    await checker.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await expect(checker.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
    await expect(
      checker.getByRole("button", { name: "Reconcile payments", exact: true }),
    ).toHaveCount(0);
    await checker.getByRole("tab", { name: "Reconciliation", exact: true }).click();
    await expect(checker.getByRole("table", { name: "Reconciliation", exact: true })).toContainText(
      reference,
    );
    const closedResponse = await checker.request.get(`${batchPath}/review?historyLimit=20`);
    expect(closedResponse.status()).toBe(200);
    const closed = payrollPaymentReviewDto.parse(await closedResponse.json());
    expect(closed.batch).toMatchObject({ status: "CLOSED", version: 2 });
    expect(closed.batch.items[0]).toMatchObject({
      status: "SUCCEEDED",
      transactionReference: reference,
    });
    expect(closed.availableActions).toEqual([]);
    expect(closed.results).toHaveLength(1);
    expect(closed.history.items.map((action) => action.kind)).toEqual([
      "PREPARED",
      "RELEASED",
      "RECONCILED",
    ]);
    expect((await checker.request.get(`${batchPath}/export`)).status()).toBe(409);
    await checker.screenshot({
      path: "../../.work/dashboard-payroll-payment-real-reconciled.png",
      fullPage: true,
      animations: "disabled",
    });

    await maker.getByRole("tab", { name: "Bank instructions", exact: true }).click();
    const revoked = await admin.request.put(`${base}/members/${makerId}`, {
      headers: headers(),
      data: {
        active: true,
        expectedVersion: 3,
        permissions: ["company.read", "payroll.read"],
        reason: "Withdraw finance authority and inspect separate payslip progress",
      },
    });
    expect(revoked.status()).toBe(200);
    await maker.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(maker.getByRole("alert")).toBeVisible();
    await expect(maker.getByRole("table", { name: "Bank instructions", exact: true })).toHaveCount(
      0,
    );
    await expect(maker.getByRole("main")).not.toContainText("001234567890");
    const progressResponse = await maker.request.get(
      `${base}/payroll/payslips/${summary.id}/payments`,
    );
    expect(progressResponse.status()).toBe(200);
    const rawProgress: unknown = await progressResponse.json();
    expect(JSON.stringify(rawProgress)).not.toContain("001234567890");
    expect(JSON.stringify(rawProgress)).not.toContain(reference);
    const progress = payrollPaymentProgressDto.parse(rawProgress);
    expect(progress.version).toBe(3);
    expect(progress.attempts).toHaveLength(1);
    expect(progress.attempts[0]).toMatchObject({ batchId, status: "SUCCEEDED" });
    await maker.goto(
      `/payroll/payslips/${summary.id}?company=${company}&from=${month}&until=${month}&scope=AUTHORIZED`,
    );
    await maker.getByRole("tab", { name: "Payment history", exact: true }).click();
    await expect(maker.getByRole("table", { name: /^Payment history/u })).toContainText("Paid");
    expect((await maker.request.get(`${batchPath}/review`)).status()).toBe(403);
  } finally {
    await maker.unroute(preparePattern, onPrepare);
    await checker.unroute(commandPattern, onCommand);
  }
}
