import { expect, type Page } from "@playwright/test";
import type { ExpenseReconciliationDto } from "../src/features/expenses/data/models/expense-reconciliation-dto";
import { installExpensePaymentsApi } from "./expense-payments-api";
import { expensePaymentRecord } from "./fixtures/expense-payments";
import { companyIds } from "./identity-api";

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
/** Transport behavior only; the real API scenario verifies authorization and persisted outcomes. */
export async function installExpenseReconciliationApi(page: Page) {
  const api = await installExpensePaymentsApi(page);
  for (const [company, records] of api.records)
    records.splice(
      0,
      records.length,
      ...records.map((_, index) =>
        expensePaymentRecord({ company: company === companyIds[0] ? 0 : 1, index: index + 1 }),
      ),
    );
  const writes: { operation: string; body: ExpenseReconciliationDto }[] = [];
  const receipts = new Map<string, { body: string; id: string; version: number }>();
  let commits = 0,
    lose = false,
    rejected: { code: string; status: number } | null = null,
    held: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/expenses/payments/*/reconcile", async (route) => {
    const request = route.request();
    expect(request.method()).toBe("POST");
    const path = new URL(request.url()).pathname.split("/"),
      company = path[4] ?? "",
      id = path[7] ?? "";
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as ExpenseReconciliationDto;
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    writes.push({ operation, body });
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
    const key = `${company}:${id}:${operation}`,
      previous = receipts.get(key);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.body);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    const record = api.records.get(company)?.find((row) => row.batch.id === id);
    if (!record)
      return route.fulfill({ status: 404, json: { code: "expense_payment_batch_not_found" } });
    if (body.expectedVersion !== record.batch.version)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    expect(record.batch.status).toBe("RELEASED");
    expect(body.reason.trim()).not.toBe("");
    const version = record.batch.version + 1,
      actorId = record.batch.releasedBy;
    if (!actorId) throw new Error("Released batch actor required");
    expect(body.results.length).toBeGreaterThan(0);
    for (const outcome of body.results) {
      const item = record.batch.items.find((value) => value.id === outcome.itemId);
      if (!item) throw new Error("Pending item required");
      expect(item.status).toBe("PENDING");
      expect(outcome.confirmedNoTransfer).toBe(outcome.status === "FAILED");
      if (outcome.status === "SUCCEEDED") expect(outcome.transactionReference).toBeTruthy();
      else expect(outcome.transactionReference).toBeNull();
      Object.assign(item, {
        status: outcome.status,
        version: 2,
        batchVersion: version,
        transactionReference: outcome.transactionReference,
        occurredAt: outcome.occurredAt,
      });
    }
    const status = record.batch.items.some((item) => item.status === "PENDING")
      ? "RELEASED"
      : "CLOSED";
    record.batch.version = version;
    record.batch.status = status;
    record.availableActions = status === "RELEASED" ? ["RECONCILE"] : [];
    record.history.items.push({
      version,
      kind: "RECONCILED",
      status,
      actorId,
      reason: body.reason,
      recordedAt: "2026-10-13T06:02:00Z",
    });
    record.results.push({
      batchVersion: version,
      actorId,
      recordedAt: "2026-10-13T06:02:00Z",
      resultingStatus: status,
      results: body.results.map(
        ({ itemId, status: resultStatus, transactionReference, occurredAt, reason }) => ({
          itemId,
          status: resultStatus,
          transactionReference,
          occurredAt,
          reason,
        }),
      ),
    });
    commits += 1;
    receipts.set(key, { body: JSON.stringify(body), id, version });
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version } });
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
