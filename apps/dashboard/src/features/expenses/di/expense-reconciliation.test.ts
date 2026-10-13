import { assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../tests/fixtures/expense-claims";
import { expensePaymentRecord } from "../../../../tests/fixtures/expense-payments";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toExpensePaymentReview } from "../data/mappers/expense-payment-review-mapper";
import type { ExpensePaymentBatchId } from "../domain/entities/expense-payment-batch";
import type { ExpensePaymentOutcome } from "../domain/entities/expense-payment-outcome";
import { expenseReconciliationSnapshot } from "../domain/entities/expense-reconciliation-snapshot";
import {
  expenseReconciliationWasRejected,
  prepareExpenseReconciliation,
} from "../domain/policies/expense-reconciliation-policy";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.pay"] },
  operation = expenseIdentifier(999) as OperationId;
const signal = () => new AbortController().signal;
function fixture() {
  const raw = expensePaymentRecord(),
    request = vi.fn();
  const review = toExpensePaymentReview(raw, company, raw.batch.id as ExpensePaymentBatchId, null),
    snapshot = expenseReconciliationSnapshot(review);
  const results: ExpensePaymentOutcome[] = snapshot.pendingItemIds.map((itemId, index) => ({
    itemId,
    status: "SUCCEEDED",
    transactionReference: `BANK-${index + 1}`,
    occurredAt: "2026-10-13T06:01:00Z",
    reason: "Bank confirmed",
    confirmedNoTransfer: false,
  }));
  const feature = createExpenseFeature(
    { request },
    { downloadBinary: vi.fn(), downloadText: vi.fn() },
  );
  const send = (outcomes = results, current = snapshot) =>
    feature.reconcilePayment.execute(
      access,
      operation,
      current,
      outcomes,
      "Checked bank statement",
      signal(),
    );
  return { raw, request, review, snapshot, results, feature, send };
}
describe("expense reconciliation boundary", () => {
  it("requires finance authority and a currently available reconciliation action", async () => {
    const f = fixture();
    expect(
      await f.feature.reviewReconciliation.execute(
        { ...access, permissions: ["expenses.manage"] },
        f.raw.batch.id,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).not.toHaveBeenCalled();
    f.raw.availableActions = ["EXPORT"];
    f.request.mockResolvedValue(f.raw);
    expect(
      await f.feature.reviewReconciliation.execute(access, f.raw.batch.id, signal()),
    ).toMatchObject({ ok: false, failure: { code: "expense_reconciliation_unavailable" } });
    f.raw.availableActions = ["RECONCILE", "EXPORT"];
    const result = await f.feature.reviewReconciliation.execute(
      access,
      f.raw.batch.id.toUpperCase(),
      signal(),
    );
    assert(result.ok);
    expect(result.value.batch.id).toBe(f.raw.batch.id);
  });
  it("does not acquire an invalid path or cancelled review", async () => {
    const f = fixture(),
      aborted = new AbortController();
    expect(
      await f.feature.reviewReconciliation.execute(access, "../other", signal()),
    ).toMatchObject({ ok: false, failure: { code: "expense_payment_batch_not_found" } });
    aborted.abort();
    await expect(
      f.feature.reviewReconciliation.execute(access, f.snapshot.id, aborted.signal),
    ).rejects.toThrow();
    expect(f.request).not.toHaveBeenCalled();
  });
  it("captures distinct success and confirmed no-transfer failure in one versioned command", async () => {
    const f = fixture(),
      first = f.results[0],
      second = f.results[1];
    assert(first && second);
    const outcomes: ExpensePaymentOutcome[] = [
      { ...first, reason: "  Bank confirmed  ", transactionReference: "  BANK-1  " },
      {
        ...second,
        status: "FAILED",
        transactionReference: null,
        confirmedNoTransfer: true,
        reason: "Destination rejected; no funds transferred",
      },
    ];
    f.request.mockResolvedValue({ id: f.snapshot.id.toUpperCase(), version: 2 });
    const result = await f.send(outcomes);
    assert(result.ok);
    expect(result.value).toEqual({ id: f.snapshot.id, version: 2 });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/expenses/payments/${f.snapshot.id}/reconcile`,
      method: "POST",
      operationId: operation,
      body: {
        expectedVersion: 1,
        results: [
          { ...first, reason: "Bank confirmed", transactionReference: "BANK-1" },
          outcomes[1],
        ],
        reason: "Checked bank statement",
      },
    });
    const prepared = prepareExpenseReconciliation(
      access,
      operation,
      f.snapshot,
      outcomes,
      "Checked",
    );
    assert(prepared.ok);
    expect(Object.isFrozen(prepared.value)).toBe(true);
    expect(Object.isFrozen(prepared.value.results)).toBe(true);
    expect(Object.isFrozen(prepared.value.results[0])).toBe(true);
    outcomes[0] = { ...first, transactionReference: "CHANGED" };
    expect(prepared.value.results[0]?.transactionReference).toBe("BANK-1");
  });
  it("rejects changed company, missing actions and invalid batch confirmation before transport", async () => {
    const f = fixture();
    expect(
      await f.feature.reconcilePayment.execute(
        { ...access, companyId: expenseIdentifier(77) as CompanyId },
        operation,
        f.snapshot,
        f.results,
        "Checked",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await f.send(f.results, { ...f.snapshot, availableActions: [] })).toMatchObject({
      ok: false,
      failure: { code: "expense_reconciliation_unavailable" },
    });
    for (const reason of ["", " \n ", "x".repeat(1001)])
      expect(
        await f.feature.reconcilePayment.execute(
          access,
          operation,
          f.snapshot,
          f.results,
          reason,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_expense_payment_action" } });
    expect(
      await f.feature.reconcilePayment.execute(
        access,
        "invalid" as OperationId,
        f.snapshot,
        f.results,
        "Checked",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_expense_payment_action" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("bounds result count and rejects repeated items, even with differently cased identifiers", async () => {
    const f = fixture(),
      first = f.results[0];
    assert(first);
    for (const outcomes of [
      [],
      Array.from({ length: 101 }, () => first),
      [first, { ...first, itemId: first.itemId.toUpperCase() }],
    ])
      expect(await f.send(outcomes)).toMatchObject({
        ok: false,
        failure: { code: "invalid_expense_payment_results" },
      });
    expect(await f.send([{ ...first, itemId: expenseIdentifier(12345) }])).toMatchObject({
      ok: false,
      failure: { code: "expense_payment_item_not_pending" },
    });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { status: "FAILED", confirmedNoTransfer: false, transactionReference: null },
    { status: "FAILED", confirmedNoTransfer: true, transactionReference: "BANK-1" },
    { status: "SUCCEEDED", confirmedNoTransfer: true },
    { transactionReference: null },
    { transactionReference: "bad\nreference" },
    { transactionReference: "x".repeat(101) },
    { reason: "  " },
    { reason: "x".repeat(1001) },
    { occurredAt: "2026-10-13T05:59:59.999999Z" },
    { occurredAt: "2026-02-30T06:01:00Z" },
  ] as const)("rejects invalid outcome $status/$occurredAt before I/O", async (invalid) => {
    const f = fixture(),
      first = f.results[0];
    assert(first);
    expect(await f.send([{ ...first, ...invalid }])).toMatchObject({
      ok: false,
      failure: { code: "invalid_expense_payment_result" },
    });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("rejects duplicated normalized bank references without mistaking failed results for duplicates", async () => {
    const f = fixture(),
      first = f.results[0],
      second = f.results[1];
    assert(first && second);
    expect(
      await f.send([first, { ...second, transactionReference: ` ${first.transactionReference} ` }]),
    ).toMatchObject({ ok: false, failure: { code: "duplicate_payment_transaction_reference" } });
    expect(f.request).not.toHaveBeenCalled();
    f.request.mockResolvedValue({ id: f.snapshot.id, version: 2 });
    expect(
      (
        await f.send(
          [first, second].map((item) => ({
            ...item,
            status: "FAILED",
            confirmedNoTransfer: true,
            transactionReference: null,
          })),
        )
      ).ok,
    ).toBe(true);
  });
  it.each([
    "stale_version",
    "expense_payment_item_not_pending",
    "invalid_expense_payment_result",
    "data_conflict",
    "recent_authentication_required",
  ])("preserves known %s rejection", async (code) => {
    const f = fixture();
    f.request.mockRejectedValue(new HttpResponseError(409, { code, detail: "PRIVATE" }));
    const result = await f.send();
    expect(result).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    expect(expenseReconciliationWasRejected({ code, fields: {}, parameters: {} })).toBe(true);
  });
  it.each([
    { id: expenseIdentifier(88), version: 2 },
    { id: expenseIdentifier(80001), version: 1 },
    { id: expenseIdentifier(80001), version: 3 },
  ])("does not trust mismatched mutation receipts ($id/$version)", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(await f.send()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(
      expenseReconciliationWasRejected({ code: "invalid_response", fields: {}, parameters: {} }),
    ).toBe(false);
  });
  it("propagates cancellation after a pending transport result", async () => {
    const f = fixture(),
      aborted = new AbortController();
    f.request.mockImplementation(async () => {
      aborted.abort();
      return { id: f.snapshot.id, version: 2 };
    });
    await expect(
      f.feature.reconcilePayment.execute(
        access,
        operation,
        f.snapshot,
        f.results,
        "Checked",
        aborted.signal,
      ),
    ).rejects.toThrow();
  });
});
