import { expect, type Page } from "@playwright/test";
import type { PayrollPaymentTransitionDto } from "../src/features/payroll/data/models/payroll-payment-transition-dto";
import { payrollPaymentRecord } from "./fixtures/payroll-payments";
import { companyIds } from "./identity-api";
import { installPayrollPaymentsApi } from "./payroll-payments-api";

function gate() {
  let enter!: () => void, release!: () => void;
  const started = new Promise<void>((done) => {
      enter = done;
    }),
    held = new Promise<void>((done) => {
      release = done;
    });
  return { started, held, enter: () => enter(), release: () => release() };
}
/** Transport fixtures; real API acceptance owns authorization and persisted transitions. */
export async function installPayrollPaymentTransitionsApi(
  page: Page,
  permissions = ["payroll.pay"],
) {
  const api = await installPayrollPaymentsApi(page, permissions);
  for (const [company, records] of api.records) {
    const scope = company === companyIds[0] ? 0 : 1;
    records.splice(
      0,
      records.length,
      ...records.map((_, index) =>
        payrollPaymentRecord({ company: scope, index: index + 1, status: "PREPARED" }),
      ),
    );
  }
  const writes: { action: string; operation: string; body: PayrollPaymentTransitionDto }[] = [];
  const receipts = new Map<string, { body: string; id: string; version: number }>();
  let commits = 0,
    lose = false,
    rejected: { code: string; status: number } | null = null,
    held: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/payroll/payments/*/*", async (route) => {
    const request = route.request();
    if (request.method() === "GET") return route.fallback();
    expect(request.method()).toBe("POST");
    const path = new URL(request.url()).pathname.split("/"),
      company = path[4] ?? "",
      id = path[7] ?? "",
      action = path[8] ?? "";
    expect(["release", "cancel"]).toContain(action);
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as PayrollPaymentTransitionDto;
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    writes.push({ action, operation, body });
    if (held) {
      const pending = held;
      held = null;
      pending.enter();
      await pending.held;
    }
    if (rejected) {
      const failure = rejected;
      rejected = null;
      return route.fulfill({
        status: failure.status,
        json: { code: failure.code, detail: "PRIVATE BANK DATA" },
      });
    }
    const key = `${company}:${id}:${action}:${operation}`,
      previous = receipts.get(key);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.body);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    const records = api.records.get(company),
      index = records?.findIndex((row) => row.batch.id === id) ?? -1,
      record = records?.[index];
    if (!record || !records)
      return route.fulfill({ status: 404, json: { code: "payroll_payment_batch_not_found" } });
    if (body.expectedVersion !== record.batch.version)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    expect(record.batch.status).toBe("PREPARED");
    expect(body.reason.trim()).not.toBe("");
    const next = payrollPaymentRecord({
      company: company === companyIds[0] ? 0 : 1,
      index: index + 1,
      status: action === "release" ? "RELEASED" : "CANCELLED",
    });
    const change = next.history.items[1];
    if (!change) throw new Error("Transition activity is required");
    change.reason = body.reason;
    records[index] = next;
    commits += 1;
    receipts.set(key, { body: JSON.stringify(body), id, version: 1 });
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version: 1 } });
  });
  return {
    ...api,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectWrite: (code: string, status = 409) => {
      rejected = { code, status };
    },
    holdWrite: () => {
      const pending = gate();
      held = pending;
      return pending;
    },
  };
}
