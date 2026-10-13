import { assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../tests/fixtures/expense-claims";
import { expensePaymentRecord } from "../../../../tests/fixtures/expense-payments";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toExpensePaymentReview } from "../data/mappers/expense-payment-review-mapper";
import type { ExpensePaymentBatchId } from "../domain/entities/expense-payment-batch";
import { expensePaymentSnapshot } from "../domain/entities/expense-payment-snapshot";
import { expensePaymentActionWasRejected } from "../domain/policies/expense-payment-transition-policy";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.pay"] },
  operation = expenseIdentifier(999) as OperationId;
const signal = () => new AbortController().signal;
function snapshot() {
  const raw = expensePaymentRecord({ status: "PREPARED" });
  return expensePaymentSnapshot(
    toExpensePaymentReview(raw, company, raw.batch.id as ExpensePaymentBatchId, null),
  );
}
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createExpenseFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
describe("expense payment transitions", () => {
  it.each(["RELEASE", "CANCEL"] as const)(
    "requires finance scope and server availability for %s review",
    async (transition) => {
      const f = fixture(),
        raw = expensePaymentRecord({ status: "PREPARED" });
      expect(
        await f.feature.reviewPaymentTransition.execute(
          { ...access, permissions: ["expenses.manage"] },
          raw.batch.id,
          transition,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(f.request).not.toHaveBeenCalled();
      raw.availableActions = [];
      f.request.mockResolvedValue(raw);
      expect(
        await f.feature.reviewPaymentTransition.execute(access, raw.batch.id, transition, signal()),
      ).toMatchObject({ ok: false, failure: { code: "expense_payment_action_unavailable" } });
      raw.availableActions = [transition];
      const review = await f.feature.reviewPaymentTransition.execute(
        access,
        raw.batch.id.toUpperCase(),
        transition,
        signal(),
      );
      assert(review.ok);
      expect(review.value.batch.id).toBe(raw.batch.id);
    },
  );
  it.each(["RELEASE", "CANCEL"] as const)(
    "sends %s with only its original version, reason and operation",
    async (transition) => {
      const f = fixture(),
        current = snapshot();
      f.request.mockResolvedValue({ id: current.id, version: 1 });
      const result = await f.feature.transitionPayment.execute(
        access,
        operation,
        current,
        transition,
        "  Bank details verified  ",
        signal(),
      );
      assert(result.ok);
      expect(result.value).toEqual({ id: current.id, version: 1 });
      expect(f.request.mock.calls[0]?.[0]).toEqual({
        path: `/api/v1/companies/${company}/expenses/payments/${current.id}/${transition.toLowerCase()}`,
        method: "POST",
        operationId: operation,
        body: { expectedVersion: 0, reason: "Bank details verified" },
      });
    },
  );
  it("does not acquire data for malformed paths or after cancellation", async () => {
    const f = fixture();
    expect(
      await f.feature.reviewPaymentTransition.execute(access, "../other", "RELEASE", signal()),
    ).toMatchObject({ ok: false, failure: { code: "expense_payment_batch_not_found" } });
    const aborted = new AbortController();
    aborted.abort();
    await expect(
      f.feature.reviewPaymentTransition.execute(access, snapshot().id, "RELEASE", aborted.signal),
    ).rejects.toThrow();
    expect(f.request).not.toHaveBeenCalled();
  });
  it("blocks wrong company, withdrawn availability and invalid confirmation input before I/O", async () => {
    const f = fixture(),
      current = snapshot();
    expect(
      await f.feature.transitionPayment.execute(
        { ...access, companyId: expenseIdentifier(77) as CompanyId },
        operation,
        current,
        "RELEASE",
        "Reviewed",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.feature.transitionPayment.execute(
        access,
        operation,
        { ...current, availableActions: ["CANCEL"] },
        "RELEASE",
        "Reviewed",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "expense_payment_action_unavailable" } });
    for (const reason of ["", " \n ", "x".repeat(1001)])
      expect(
        await f.feature.transitionPayment.execute(
          access,
          operation,
          current,
          "CANCEL",
          reason,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_expense_payment_action" } });
    expect(
      await f.feature.transitionPayment.execute(
        access,
        "invalid" as OperationId,
        current,
        "CANCEL",
        "Reviewed",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_expense_payment_action" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    "stale_version",
    "expense_payment_not_prepared",
    "independent_payment_release_required",
    "independent_expense_payment_required",
    "recent_authentication_required",
  ])("preserves a rejected %s result", async (code) => {
    const f = fixture();
    f.request.mockRejectedValue(new HttpResponseError(409, { code, detail: "PRIVATE" }));
    const result = await f.feature.transitionPayment.execute(
      access,
      operation,
      snapshot(),
      "RELEASE",
      "Reviewed",
      signal(),
    );
    expect(result).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    expect(expensePaymentActionWasRejected({ code, fields: {}, parameters: {} })).toBe(true);
  });
  it.each([
    { id: expenseIdentifier(88), version: 1 },
    { id: snapshot().id, version: 0 },
    { id: snapshot().id, version: 2 },
  ])("keeps an untrusted receipt unconfirmed ($id/$version)", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.feature.transitionPayment.execute(
        access,
        operation,
        snapshot(),
        "RELEASE",
        "Reviewed",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(
      expensePaymentActionWasRejected({ code: "invalid_response", fields: {}, parameters: {} }),
    ).toBe(false);
  });
  it("propagates cancellation after a pending command without publishing its receipt", async () => {
    const f = fixture(),
      current = snapshot(),
      aborted = new AbortController();
    f.request.mockImplementation(async () => {
      aborted.abort();
      return { id: current.id, version: 1 };
    });
    await expect(
      f.feature.transitionPayment.execute(
        access,
        operation,
        current,
        "CANCEL",
        "Reviewed",
        aborted.signal,
      ),
    ).rejects.toThrow();
  });
});
