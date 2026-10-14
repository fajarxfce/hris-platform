import { assert, describe, expect, it, vi } from "vitest";
import { payrollIdentifier } from "../../../../tests/fixtures/payroll-payment-identifiers";
import { payrollPaymentRecord } from "../../../../tests/fixtures/payroll-payments";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toPayrollPaymentReview } from "../data/mappers/payroll-payment-review-mapper";
import type { PayrollPaymentBatchId } from "../domain/entities/payroll-payment-batch";
import { payrollPaymentSnapshot } from "../domain/entities/payroll-payment-snapshot";
import { payrollPaymentActionWasRejected } from "../domain/policies/payroll-payment-transition-policy";
import { createPayrollFeature } from "./payroll-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["payroll.pay"] },
  operation = payrollIdentifier(999) as OperationId;
const signal = () => new AbortController().signal;
function snapshot() {
  const raw = payrollPaymentRecord({ status: "PREPARED" });
  return payrollPaymentSnapshot(
    toPayrollPaymentReview(raw, company, raw.batch.id as PayrollPaymentBatchId, null),
  );
}
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createPayrollFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
describe("payroll payment transitions", () => {
  it.each(["RELEASE", "CANCEL"] as const)(
    "requires finance scope and server availability for %s review",
    async (transition) => {
      const f = fixture(),
        raw = payrollPaymentRecord({ status: "PREPARED" });
      expect(
        await f.feature.reviewPaymentTransition.execute(
          { ...access, permissions: ["payroll.manage"] },
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
      ).toMatchObject({ ok: false, failure: { code: "payroll_payment_action_unavailable" } });
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
        path: `/api/v1/companies/${company}/payroll/payments/${current.id}/${transition.toLowerCase()}`,
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
    ).toMatchObject({ ok: false, failure: { code: "payroll_payment_batch_not_found" } });
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
        { ...access, companyId: payrollIdentifier(77) as CompanyId },
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
    ).toMatchObject({ ok: false, failure: { code: "payroll_payment_action_unavailable" } });
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
      ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_payment_action" } });
    expect(
      await f.feature.transitionPayment.execute(
        access,
        "invalid" as OperationId,
        current,
        "CANCEL",
        "Reviewed",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_payment_action" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    "stale_version",
    "payroll_payment_not_prepared",
    "independent_payment_release_required",
    "access_denied",
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
    expect(payrollPaymentActionWasRejected({ code, fields: {}, parameters: {} })).toBe(true);
  });
  it.each([
    { id: payrollIdentifier(88), version: 1 },
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
      payrollPaymentActionWasRejected({ code: "invalid_response", fields: {}, parameters: {} }),
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
