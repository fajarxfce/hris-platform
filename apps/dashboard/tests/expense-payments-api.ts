import { expect, type Page } from "@playwright/test";
import {
  expensePaymentCsv,
  expensePaymentList,
  expensePaymentRecord,
  expensePaymentReviewPage,
} from "./fixtures/expense-payments";
import { companyIds, installIdentityApi } from "./identity-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
type Kind = "list" | "review" | "worksheet";
export async function installExpensePaymentsApi(page: Page, permissions = ["expenses.pay"]) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map(
    companyIds.map((company, index) => [
      company as string,
      Array.from({ length: index === 0 ? 23 : 1 }, (_, row) =>
        expensePaymentRecord({
          company: index,
          index: row + 1,
          settled: row === 0 ? 23 : 0,
          count: row === 0 ? 25 : 3,
        }),
      ),
    ]),
  );
  const reads: { kind: Kind; url: URL }[] = [];
  let rejected: { kind: Kind; code: string } | null = null,
    malformed = false;
  let held: {
    kind: Kind;
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/expenses/payments**", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url()),
      path = url.pathname.split("/"),
      company = path[4] ?? "",
      id = path[7];
    const kind: Kind = path[8] === "export" ? "worksheet" : id ? "review" : "list";
    reads.push({ kind, url });
    const rejection = rejected?.kind === kind ? rejected.code : null;
    if (rejection) rejected = null;
    const wait = held;
    if (wait?.kind === kind) {
      held = null;
      wait.entered.resolve();
      await wait.released.promise;
    }
    if (rejection)
      return route.fulfill({ status: 403, json: { code: rejection, detail: "PRIVATE BANK DATA" } });
    const available = records.get(company) ?? [];
    if (kind === "list") {
      expect(url.searchParams.get("limit")).toBe("20");
      const status = url.searchParams.get("status"),
        filtered = available.filter((record) => status === null || record.batch.status === status);
      const after = url.searchParams.get("after"),
        start = after === null ? 0 : filtered.findIndex((record) => record.batch.id === after) + 1;
      return route.fulfill({ json: expensePaymentList(filtered.slice(start)) });
    }
    const record = available.find((row) => row.batch.id === id);
    if (!record)
      return route.fulfill({ status: 404, json: { code: "expense_payment_batch_not_found" } });
    if (kind === "worksheet")
      return route.fulfill({
        status: 200,
        headers: { "Content-Type": "text/csv;charset=UTF-8", "Cache-Control": "private, no-store" },
        body: expensePaymentCsv,
      });
    expect(url.searchParams.get("historyLimit")).toBe("20");
    const cursor = url.searchParams.get("historyAfter"),
      reply = expensePaymentReviewPage(record, cursor === null ? null : Number(cursor));
    if (malformed) {
      malformed = false;
      return route.fulfill({ json: { ...reply, batch: { ...reply.batch, totalAmount: "1.00" } } });
    }
    return route.fulfill({ json: reply });
  });
  return {
    identity,
    records,
    reads,
    rejectNext: (kind: Kind, code: string) => {
      rejected = { kind, code };
    },
    malformedReview: () => {
      malformed = true;
    },
    holdNext: (kind: Kind) => {
      const entered = gate(),
        released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
