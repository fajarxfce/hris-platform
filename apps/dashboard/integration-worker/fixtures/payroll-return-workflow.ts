import { randomUUID } from "node:crypto";
import { type BrowserContext, expect, type Page, type Route } from "@playwright/test";
import { bankConfirmationInput } from "../../integration/fixtures/bank-confirmation-input";
import { payrollCalculationMinor } from "../../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import { payrollPaymentReviewDto } from "../../src/features/payroll/data/models/payroll-payment-dto";
import {
  type ConfirmPayrollPaymentRecoveryDto,
  payrollPaymentRecoveryReviewDto,
} from "../../src/features/payroll/data/models/payroll-payment-recovery-dto";
import {
  type PayrollPayslipSummaryDto,
  payrollPayslipDto,
} from "../../src/features/payroll/data/models/payroll-payslip-dto";
import { payrollAmountView } from "../../src/features/payroll/presentation/models/payroll-policy-view";
import { publishNegativePayrollAmendment } from "./payroll-amendment-publication";

/** Exercise real return entry while retaining the original bank and payroll records. */
export async function exercisePayrollReturns(input: {
  author: BrowserContext;
  publisher: Page;
  finance: Page;
  admin: BrowserContext;
  company: string;
  month: string;
  summary: PayrollPayslipSummaryDto;
  batchId: string;
  headers: () => Record<string, string>;
}) {
  const { author, publisher, finance, admin, company, month, summary, batchId, headers } = input,
    base = `/api/v1/companies/${company}`,
    payslip = `${base}/payroll/payslips/${summary.id}`,
    recovery = `${payslip}/recoveries`,
    batch = `${base}/payroll/payments/${batchId}`;
  const corrected = await publishNegativePayrollAmendment({
    author,
    publisher: publisher.context(),
    reviewer: finance.context(),
    admin,
    company,
    assessment: summary.id,
    month,
    adminHeaders: headers,
  });
  const beforeResponse = await finance.request.get(`${recovery}/review`);
  expect(beforeResponse.status()).toBe(200);
  const before = payrollPaymentRecoveryReviewDto.parse(await beforeResponse.json());
  expect(before.canConfirm).toBe(true);
  expect(before.recoveries).toEqual([]);
  expect(before.payments.version).toBe(4);
  expect(before.payments.settlement.assessmentRevision).toBe(1);
  expect(payrollCalculationMinor(before.payments.settlement.recoverable) > 10_100n).toBe(true);
  expect((await finance.request.get(payslip)).status()).toBe(403);
  const originalResponse = await publisher.request.get(`${payslip}?revision=0`);
  expect(originalResponse.status()).toBe(200);
  expect(payrollPayslipDto.parse(await originalResponse.json()).summary).toEqual(summary);
  const revisedResponse = await publisher.request.get(payslip);
  expect(revisedResponse.status()).toBe(200);
  const revised = payrollPayslipDto.parse(await revisedResponse.json());
  expect(revised.summary.version).toBe(1);
  expect(payrollCalculationMinor(revised.summary.takeHome)).toBe(
    payrollCalculationMinor(before.payments.settlement.entitlement),
  );

  const writes: {
      path: string;
      operation: string;
      body: ConfirmPayrollPaymentRecoveryDto;
      status: number;
    }[] = [],
    pattern = `**${recovery}/*`;
  let dropped = false;
  const onReturn = async (route: Route) => {
    const request = route.request();
    if (request.method() !== "PUT") return route.continue();
    const response = await route.fetch();
    writes.push({
      path: new URL(request.url()).pathname,
      operation: request.headers()["idempotency-key"] ?? "",
      body: request.postDataJSON() as ConfirmPayrollPaymentRecoveryDto,
      status: response.status(),
    });
    if (response.status() === 200 && !dropped) {
      dropped = true;
      return route.abort("failed");
    }
    return route.fulfill({ response });
  };
  await finance.route(pattern, onReturn);
  try {
    await finance.goto(`/payroll/payments/${batchId}?company=${company}&view=results`);
    await finance
      .getByRole("button", {
        name: `View payment: ${summary.employeeName} · ${summary.employeeNumber} · ${month}`,
        exact: true,
      })
      .click();
    await finance
      .getByRole("dialog", { name: "Payment details", exact: true })
      .getByRole("link", { name: "View balance and returns", exact: true })
      .click();
    await expect(
      finance.getByRole("region", { name: "Payment balance", exact: true }),
    ).toContainText(payrollAmountView(before.payments.settlement.recoverable, "en"));
    await finance.getByRole("button", { name: "Record return", exact: true }).click();
    const reference = `BROWSER-RETURN-${randomUUID()}`,
      confirmed = await bankConfirmationInput(before.earliestConfirmationAt, "Asia/Jakarta");
    await finance.getByLabel("Returned amount (IDR)", { exact: true }).fill("100.00");
    await finance.getByLabel("Bank transaction reference", { exact: true }).fill(reference);
    await finance
      .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
      .fill(confirmed);
    await finance.getByLabel("Return note", { exact: true }).fill("Confirmed partial return");

    const concurrent = await publisher.request.put(`${recovery}/${randomUUID()}`, {
      headers: corrected.publisherHeaders(),
      data: {
        assessmentRevision: before.payments.settlement.assessmentRevision,
        paymentVersion: before.payments.version,
        amount: "1.00",
        transactionReference: `COMPETING-${randomUUID()}`,
        occurredAt: new Date().toISOString(),
        reason: "Independent confirmed return",
      },
    });
    expect(concurrent.status()).toBe(200);
    await finance.getByRole("button", { name: "Confirm return", exact: true }).click();
    await expect(finance.getByRole("alert")).toContainText("The payment balance changed.");
    await expect(
      finance.getByRole("button", { name: "Confirm return", exact: true }),
    ).toBeDisabled();
    await finance.getByRole("button", { name: "Reload review", exact: true }).click();
    await finance
      .getByRole("dialog")
      .getByRole("button", { name: "Leave page", exact: true })
      .click();
    await expect(finance.getByLabel("Bank transaction reference", { exact: true })).toHaveValue("");
    await finance.getByLabel("Returned amount (IDR)", { exact: true }).fill("100.00");
    await finance.getByLabel("Bank transaction reference", { exact: true }).fill(reference);
    await finance
      .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
      .fill(confirmed);
    await finance.getByLabel("Return note", { exact: true }).fill("Confirmed partial return");
    await finance.getByRole("button", { name: "Confirm return", exact: true }).click();
    await finance.getByRole("button", { name: "Retry original return", exact: true }).click();
    await expect(finance.getByRole("status")).toContainText("Return recorded.");
    expect(writes.map((write) => write.status)).toEqual([409, 200, 200]);
    expect(writes[2]).toEqual(writes[1]);
    expect(writes[1]?.operation).not.toBe(writes[0]?.operation);
    const original = writes[1];
    if (!original) throw new Error("Confirmed original return required");
    expect(original.body.paymentVersion).toBe(5);
    await finance.getByRole("link", { name: "View balance and returns", exact: true }).click();
    const partialResponse = await finance.request.get(`${recovery}/review`);
    expect(partialResponse.status()).toBe(200);
    const partial = payrollPaymentRecoveryReviewDto.parse(await partialResponse.json());
    expect(partial.payments.version).toBe(6);
    expect(partial.recoveries).toHaveLength(2);
    expect(partial.payments.settlement.confirmedRecovered).toBe("101.00");
    await expect(
      finance.getByRole("table", { name: "Return history (Asia/Jakarta)", exact: true }),
    ).toContainText(reference);

    await finance.getByRole("button", { name: "Record return", exact: true }).click();
    await finance
      .getByLabel("Returned amount (IDR)", { exact: true })
      .fill(partial.payments.settlement.recoverable);
    const finalReference = `BROWSER-FINAL-${randomUUID()}`;
    await finance.getByLabel("Bank transaction reference", { exact: true }).fill(finalReference);
    await finance
      .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
      .fill(confirmed);
    await finance.getByLabel("Return note", { exact: true }).fill("Confirmed remaining return");
    await finance.getByRole("button", { name: "Confirm return", exact: true }).click();
    await expect(finance.getByRole("status")).toContainText("Return recorded.");
    await finance.getByRole("link", { name: "View balance and returns", exact: true }).click();
    const afterResponse = await finance.request.get(`${recovery}/review`);
    expect(afterResponse.status()).toBe(200);
    const after = payrollPaymentRecoveryReviewDto.parse(await afterResponse.json());
    expect(after.payments.version).toBe(7);
    expect(after.recoveries).toHaveLength(3);
    expect(after.payments.settlement.recoverable).toBe("0.00");
    expect(after.payments.settlement.payable).toBe("0.00");
    expect(after.canConfirm).toBe(false);
    expect(after.blockers).toEqual(["NOT_OVERPAID"]);
    expect(after.payments.attempts).toEqual(before.payments.attempts);
    expect(after.payments.settlement.confirmedRecovered).toBe(
      before.payments.settlement.recoverable,
    );
    await expect(finance.getByRole("button", { name: "Record return", exact: true })).toHaveCount(
      0,
    );
    const replay = await finance.request.put(original.path, {
      headers: {
        [corrected.reviewerToken.headerName]: corrected.reviewerToken.token,
        "Idempotency-Key": original.operation,
      },
      data: original.body,
    });
    expect(replay.status()).toBe(200);
    expect((await replay.json()).id).toBe(original.path.split("/").at(-1));
    const bank = payrollPaymentReviewDto.parse(
      await (await finance.request.get(`${batch}/review?historyLimit=20`)).json(),
    );
    expect(bank.batch.status).toBe("CLOSED");
    expect(bank.batch.items).toHaveLength(1);
    expect(payrollCalculationMinor(bank.batch.items[0]?.amount ?? "")).toBe(
      payrollCalculationMinor(summary.takeHome),
    );
    expect(bank.results).toHaveLength(1);
    await finance.screenshot({
      path: "../../.work/dashboard-payroll-returns-real.png",
      fullPage: true,
      animations: "disabled",
    });

    const withdrawn = await admin.request.put(
      `${base}/members/a0000000-0000-4000-8000-000000000054`,
      {
        headers: headers(),
        data: {
          expectedVersion: corrected.financeVersion,
          active: true,
          permissions: ["company.read"],
          reason: "Withdraw finance return authority",
        },
      },
    );
    expect(withdrawn.status()).toBe(200);
    await finance.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(finance.getByRole("alert")).toBeVisible();
    await expect(finance.getByRole("region", { name: "Payment balance", exact: true })).toHaveCount(
      0,
    );
    await expect(
      finance.getByRole("table", { name: "Return history (Asia/Jakarta)", exact: true }),
    ).toHaveCount(0);
    await expect(finance.getByRole("main")).not.toContainText(reference);
    expect((await finance.request.get(`${recovery}/review`)).status()).toBe(403);
  } finally {
    await finance.unroute(pattern, onReturn);
  }
}
