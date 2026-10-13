import { expect, type Page } from "@playwright/test";
import type { ExpensePaymentPreparationDto } from "../src/features/expenses/data/models/expense-payment-preparation-dto";
import { expenseAmountMinor } from "../src/features/expenses/domain/policies/expense-amount";
import { installExpensePaymentsApi } from "./expense-payments-api";
import { expensePayable } from "./fixtures/expense-payables";
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
export async function installExpensePreparationApi(page: Page, permissions = ["expenses.pay"]) {
  const api = await installExpensePaymentsApi(page, permissions);
  const payables = new Map(
    companyIds.map((company, scope) => [
      company as string,
      Array.from({ length: scope === 0 ? 23 : 1 }, (_, index) => expensePayable(index, scope)),
    ]),
  );
  const ineligible = payables.get(companyIds[0])?.[4];
  if (ineligible) ineligible.availableActions = [];
  const payableReads: URL[] = [],
    writes: {
      company: string;
      id: string;
      operation: string;
      body: ExpensePaymentPreparationDto;
    }[] = [];
  const receipts = new Map<string, { body: string; id: string; version: number }>();
  let commits = 0,
    lose = false,
    readFailure: string | null = null,
    writeFailure: { code: string; status: number } | null = null,
    readGate: ReturnType<typeof gate> | null = null,
    writeGate: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/expenses/payments**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.split("/"),
      company = path[4] ?? "",
      id = path[7] ?? "";
    if (request.method() === "GET" && id === "payables") {
      payableReads.push(url);
      if (readGate) {
        const pending = readGate;
        readGate = null;
        pending.enter();
        await pending.held;
      }
      if (readFailure) {
        const code = readFailure;
        readFailure = null;
        return route.fulfill({ status: 403, json: { code, detail: "PRIVATE BANK DATA" } });
      }
      expect(url.searchParams.get("limit")).toBe("20");
      const rows = payables.get(company) ?? [],
        after = url.searchParams.get("after"),
        start = after === null ? 0 : rows.findIndex((row) => row.claimId === after) + 1;
      const items = rows.slice(start, start + 20);
      return route.fulfill({
        json: { items, nextCursor: start + 20 < rows.length ? items.at(-1)?.claimId : null },
      });
    }
    if (request.method() !== "PUT") return route.fallback();
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as ExpensePaymentPreparationDto;
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    writes.push({ company, id, operation, body });
    if (writeGate) {
      const pending = writeGate;
      writeGate = null;
      pending.enter();
      await pending.held;
    }
    if (writeFailure) {
      const failure = writeFailure;
      writeFailure = null;
      return route.fulfill({
        status: failure.status,
        json: { code: failure.code, detail: "PRIVATE BANK DATA" },
      });
    }
    const key = `${company}:${operation}`,
      previous = receipts.get(key);
    if (previous) {
      expect(id).toBe(previous.id);
      expect(JSON.stringify(body)).toBe(previous.body);
      return route.fulfill({ json: { id, version: 0 } });
    }
    const record = expensePaymentRecord({ status: "PREPARED", count: body.items.length }),
      rows = payables.get(company) ?? [];
    let total = 0n;
    record.batch.id = id;
    record.batch.title = body.title;
    record.batch.createdAt = "2026-10-13T05:59:00Z";
    record.batch.items = body.items.map((item, index) => {
      const payable = rows.find((row) => row.submissionId === item.submissionId),
        baseline = record.batch.items[index];
      if (!payable || !baseline) throw new Error("Selected payable and instruction are required");
      expect(payable.availableActions).toContain("PREPARE");
      total += expenseAmountMinor(payable.amount) ?? 0n;
      return { ...baseline, ...payable, id: item.id, destination: item.destination };
    });
    record.batch.totalAmount = `${total / 100n}.${String(total % 100n).padStart(2, "0")}`;
    const entry = record.history.items[0];
    if (!entry) throw new Error("Initial history is required");
    entry.reason = body.reason;
    entry.recordedAt = record.batch.createdAt;
    record.availableActions = ["CANCEL"];
    api.records.get(company)?.unshift(record);
    payables.set(
      company,
      rows.filter((row) => !body.items.some((item) => item.submissionId === row.submissionId)),
    );
    commits += 1;
    receipts.set(key, { body: JSON.stringify(body), id, version: 0 });
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version: 0 } });
  });
  return {
    ...api,
    payables,
    payableReads,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectRead: (code: string) => {
      readFailure = code;
    },
    rejectWrite: (code: string, status = 409) => {
      writeFailure = { code, status };
    },
    holdRead: () => {
      const pending = gate();
      readGate = pending;
      return pending;
    },
    holdWrite: () => {
      const pending = gate();
      writeGate = pending;
      return pending;
    },
  };
}
