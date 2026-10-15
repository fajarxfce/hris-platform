import { expect, type Page } from "@playwright/test";
import type { ConfirmPayrollPaymentRecoveryDto } from "../src/features/payroll/data/models/payroll-payment-recovery-dto";
import { payrollRecoveryReview } from "./fixtures/payroll-payment-recoveries";
import { companyIds } from "./identity-api";
import { installPayrollPaymentsApi } from "./payroll-payments-api";

function gate() {
  let enter!: () => void, release!: () => void;
  const entered = new Promise<void>((done) => {
      enter = done;
    }),
    waiting = new Promise<void>((done) => {
      release = done;
    });
  return { entered, waiting, enter: () => enter(), release: () => release() };
}
const minor = (value: string) => {
  const [whole = "0", fraction = ""] = value.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
};
const amount = (value: bigint) => `${value / 100n}.${String(value % 100n).padStart(2, "0")}`;

/** Transport fixture only; real API/worker acceptance verifies financial policy and storage. */
export async function installPayrollPaymentRecoveriesApi(
  page: Page,
  permissions = ["payroll.pay"],
) {
  const api = await installPayrollPaymentsApi(page, permissions),
    reviews = new Map(
      companyIds.map((company, index) => [company as string, payrollRecoveryReview(index)]),
    ),
    reads: string[] = [],
    writes: {
      company: string;
      assessment: string;
      id: string;
      operation: string;
      body: ConfirmPayrollPaymentRecoveryDto;
    }[] = [],
    receipts = new Map<string, { id: string; body: string }>();
  let commits = 0,
    lose = false,
    rejected: { method: string; code: string; status: number } | null = null,
    pending: { method: string; barrier: ReturnType<typeof gate> } | null = null;
  await page.route("**/api/v1/companies/*/payroll/payslips/*/recoveries/*", async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname.split("/"),
      company = path[4] ?? "",
      assessment = path[7] ?? "",
      id = path[9] ?? "",
      method = request.method();
    expect(["GET", "PUT"]).toContain(method);
    const record = reviews.get(company),
      rejection = rejected?.method === method ? rejected : null;
    if (rejection) rejected = null;
    if (method === "GET") reads.push(request.url());
    const write =
      method === "PUT"
        ? {
            company,
            assessment,
            id,
            operation: request.headers()["idempotency-key"] ?? "",
            body: request.postDataJSON() as ConfirmPayrollPaymentRecoveryDto,
          }
        : null;
    if (write) writes.push(write);
    const held = pending?.method === method ? pending.barrier : null;
    if (held) {
      pending = null;
      held.enter();
      await held.waiting;
    }
    if (rejection)
      return route.fulfill({
        status: rejection.status,
        json: { code: rejection.code, detail: "PRIVATE FINANCE DATA" },
      });
    if (!record || record.assessmentId !== assessment)
      return route.fulfill({ status: 404, json: { code: "payroll_payslip_not_found" } });
    if (!write) {
      expect(id).toBe("review");
      return route.fulfill({ json: record });
    }
    expect(write.operation).toMatch(/^[0-9a-f-]{36}$/u);
    const key = `${company}:${assessment}:${write.operation}`,
      previous = receipts.get(key);
    if (previous) {
      expect(write.id).toBe(previous.id);
      expect(JSON.stringify(write.body)).toBe(previous.body);
      return route.fulfill({ json: { id: previous.id, version: 0 } });
    }
    if (
      write.body.assessmentRevision !== record.payments.settlement.assessmentRevision ||
      write.body.paymentVersion !== record.payments.version
    )
      return route.fulfill({ status: 409, json: { code: "payroll_payment_balance_changed" } });
    const returned = minor(write.body.amount),
      remaining = minor(record.payments.settlement.recoverable) - returned;
    expect(returned > 0n && remaining >= 0n).toBe(true);
    expect(write.body.transactionReference.length).toBeGreaterThan(0);
    record.recoveries.push({
      id,
      assessmentId: assessment,
      ...write.body,
      currency: "IDR",
      actorId: "a0000000-0000-4000-8000-000000000001",
      recordedAt: "2026-10-14T07:00:00Z",
    });
    record.payments.version += 1;
    record.payments.settlement.confirmedRecovered = amount(
      minor(record.payments.settlement.confirmedRecovered) + returned,
    );
    record.payments.settlement.recoverable = amount(remaining);
    record.canConfirm = remaining > 0n;
    record.blockers = remaining === 0n ? ["NOT_OVERPAID"] : [];
    commits += 1;
    receipts.set(key, { id, body: JSON.stringify(write.body) });
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version: 0 } });
  });
  return {
    ...api,
    reviews,
    recoveryReads: reads,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectRecovery: (method: "GET" | "PUT", code: string, status = 403) => {
      rejected = { method, code, status };
    },
    holdRecovery: (method: "GET" | "PUT") => {
      const barrier = gate();
      pending = { method, barrier };
      return barrier;
    },
  };
}
