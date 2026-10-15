import { assert, describe, expect, it, vi } from "vitest";
import { payrollIdentifier } from "../../../../tests/fixtures/payroll-payment-identifiers";
import {
  payrollRecoveryInput,
  payrollRecoveryReview,
} from "../../../../tests/fixtures/payroll-payment-recoveries";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toPayrollPaymentRecoveryReview } from "../data/mappers/payroll-payment-recovery-mapper";
import type { PayrollPaymentRecoveryReviewDto } from "../data/models/payroll-payment-recovery-dto";
import { payrollPaymentRecoverySnapshot } from "../domain/entities/payroll-payment-recovery-snapshot";
import { createPayrollFeature } from "./payroll-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  access = { companyId: company, permissions: ["payroll.pay"] },
  operation = payrollIdentifier(99001) as OperationId,
  id = payrollIdentifier(99002),
  signal = () => new AbortController().signal;
function fixture(raw = payrollRecoveryReview()) {
  const request = vi.fn().mockResolvedValue(raw);
  return {
    raw,
    request,
    actions: createPayrollFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
function snapshot(raw = payrollRecoveryReview()) {
  return payrollPaymentRecoverySnapshot(
    toPayrollPaymentRecoveryReview(raw, company, raw.assessmentId),
  );
}
describe("finance return application and transport boundaries", () => {
  it.each(
    [[], ["payroll.read"], ["payroll.amend"], ["expenses.pay"]].map((permissions) => ({
      permissions,
    })),
  )(
    "requires finance authority independently of payslip reading ($permissions)",
    async ({ permissions }) => {
      const f = fixture(),
        actor = { ...access, permissions };
      expect(
        await f.actions.reviewPaymentRecovery.execute(actor, f.raw.assessmentId, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await f.actions.confirmPaymentRecovery.execute(
          actor,
          operation,
          id,
          snapshot(),
          payrollRecoveryInput,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it("normalizes scoped identifiers and isolates retained facts from raw response mutation", async () => {
    const f = fixture(),
      result = await f.actions.reviewPaymentRecovery.execute(
        access,
        f.raw.assessmentId.toUpperCase(),
        signal(),
      );
    assert(result.ok);
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/payroll/payslips/${f.raw.assessmentId}/recoveries/review`,
    });
    expect(result.value.companyId).toBe(company);
    expect(result.value.payments.settlement.recoverable).toBe("900.25");
    expect(result.value.earliestConfirmationAt).toBe("2026-10-14T06:00:00.123456Z");
    f.raw.blockers.push("BENEFICIARY");
    const row = f.raw.recoveries[0];
    assert(row);
    row.reason = "Changed raw data";
    expect(result.value.blockers).toEqual([]);
    expect(result.value.recoveries[0]?.reason).toBe("Confirmed incoming bank transfer");
    expect(Object.isFrozen(result.value.recoveries[0])).toBe(true);
    expect(Object.isFrozen(result.value.payments.settlement)).toBe(true);
  });
  it.each<[string, (raw: PayrollPaymentRecoveryReviewDto) => void]>([
    [
      "a different payslip",
      (raw) => {
        raw.assessmentId = payrollIdentifier(7);
      },
    ],
    [
      "a different payment projection",
      (raw) => {
        raw.payments.assessmentId = payrollIdentifier(7);
      },
    ],
    [
      "incoherent amounts",
      (raw) => {
        raw.payments.settlement.recoverable = "900.24";
      },
    ],
    [
      "omitted return history",
      (raw) => {
        raw.recoveries = [];
      },
    ],
    [
      "a duplicated return",
      (raw) => {
        const row = raw.recoveries[0];
        assert(row);
        raw.recoveries.push({ ...row });
      },
    ],
    [
      "a different assessment return",
      (raw) => {
        const row = raw.recoveries[0];
        assert(row);
        row.assessmentId = payrollIdentifier(7);
      },
    ],
    [
      "an unobserved assessment revision",
      (raw) => {
        const row = raw.recoveries[0];
        assert(row);
        row.assessmentRevision = 2;
      },
    ],
    [
      "a future payment version",
      (raw) => {
        const row = raw.recoveries[0];
        assert(row);
        row.paymentVersion = 5;
      },
    ],
    [
      "an oversized return",
      (raw) => {
        const row = raw.recoveries[0];
        assert(row);
        row.amount = "1000000000000.00";
      },
    ],
    [
      "a bank time after recording",
      (raw) => {
        const row = raw.recoveries[0];
        assert(row);
        row.recordedAt = "2026-10-14T06:09:59.999999Z";
      },
    ],
    [
      "a missing successful bank time",
      (raw) => {
        const row = raw.payments.attempts[0];
        assert(row);
        row.resolvedAt = null;
      },
    ],
    [
      "an imprecise lower time bound",
      (raw) => {
        raw.earliestConfirmationAt = "2026-10-14T06:00:00.123455Z";
      },
    ],
    [
      "a bypassed beneficiary blocker",
      (raw) => {
        raw.blockers = ["BENEFICIARY"];
      },
    ],
    [
      "duplicated blockers",
      (raw) => {
        raw.canConfirm = false;
        raw.blockers = ["BENEFICIARY", "BENEFICIARY"];
      },
    ],
  ])("rejects %s inside the safe data boundary", async (_, mutate) => {
    const f = fixture(),
      assessment = f.raw.assessmentId;
    mutate(f.raw);
    expect(
      await f.actions.reviewPaymentRecovery.execute(access, assessment, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("takes a successful transfer after publication as the earliest bank time", async () => {
    const f = fixture(),
      row = f.raw.payments.attempts[0];
    assert(row);
    row.resolvedAt = "2026-10-14T06:01:00.000002Z";
    f.raw.earliestConfirmationAt = row.resolvedAt;
    assert(
      (await f.actions.reviewPaymentRecovery.execute(access, f.raw.assessmentId, signal())).ok,
    );
  });
  it("validates amount, exact observed time, reason and company before issuing a command", async () => {
    const f = fixture();
    const bad = [
      { ...payrollRecoveryInput, amount: "900.26" },
      { ...payrollRecoveryInput, amount: "-1" },
      { ...payrollRecoveryInput, amount: "0" },
      { ...payrollRecoveryInput, amount: "1.001" },
      { ...payrollRecoveryInput, amount: "1e2" },
      { ...payrollRecoveryInput, amount: "1".repeat(5000) },
      { ...payrollRecoveryInput, occurredAt: "2026-10-14T06:00:00.123455Z" },
      { ...payrollRecoveryInput, occurredAt: "2026-10-14T06:00:00+00:00" },
      { ...payrollRecoveryInput, reason: "  " },
      { ...payrollRecoveryInput, reason: "x".repeat(1001) },
      { ...payrollRecoveryInput, transactionReference: "<script>" },
    ];
    for (const input of bad)
      expect(
        await f.actions.confirmPaymentRecovery.execute(
          access,
          operation,
          id,
          snapshot(),
          input,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_payment_recovery" } });
    expect(
      await f.actions.confirmPaymentRecovery.execute(
        { ...access, companyId: payrollIdentifier(99) as CompanyId },
        operation,
        id,
        snapshot(),
        payrollRecoveryInput,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.actions.confirmPaymentRecovery.execute(
        access,
        operation,
        id,
        { ...snapshot(), canConfirm: false },
        payrollRecoveryInput,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "payroll_payment_recovery_unavailable" } });
    expect(
      await f.actions.reviewPaymentRecovery.execute(access, "../private", signal()),
    ).toMatchObject({ ok: false, failure: { code: "payroll_payslip_not_found" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("freezes observed revisions and exact decimals in the explicit original command", async () => {
    const f = fixture(),
      at = "2026-10-14T06:00:00.123456Z";
    f.request.mockResolvedValue({ id, version: 0 });
    const input = {
      ...payrollRecoveryInput,
      amount: " 900.25 ",
      reason: " Bank confirmed ",
      occurredAt: at,
    };
    const result = await f.actions.confirmPaymentRecovery.execute(
      access,
      operation,
      id.toUpperCase(),
      snapshot(),
      input,
      signal(),
    );
    assert(result.ok);
    expect(result.value).toEqual({ id, version: 0 });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/payroll/payslips/${f.raw.assessmentId}/recoveries/${id}`,
      method: "PUT",
      operationId: operation,
      body: {
        assessmentRevision: 1,
        paymentVersion: 5,
        amount: "900.25",
        transactionReference: "RETURN-NEW",
        occurredAt: at,
        reason: "Bank confirmed",
      },
    });
  });
  it("supports an exact partial return against a balance larger than one instruction", async () => {
    const f = fixture();
    f.request.mockResolvedValue({ id, version: 0 });
    const large = { ...snapshot(), recoverable: "1000000000000.02" };
    assert(
      (
        await f.actions.confirmPaymentRecovery.execute(
          access,
          operation,
          id,
          large,
          { ...payrollRecoveryInput, amount: "999999999999.99" },
          signal(),
        )
      ).ok,
    );
    expect(
      await f.actions.confirmPaymentRecovery.execute(
        access,
        operation,
        id,
        large,
        { ...payrollRecoveryInput, amount: "1000000000000.00" },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_payment_recovery" } });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  it.each([
    { id: payrollIdentifier(1), version: 0 },
    { id, version: 1 },
  ])("rejects an unrelated or mutable confirmation receipt", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.actions.confirmPaymentRecovery.execute(
        access,
        operation,
        id,
        snapshot(),
        payrollRecoveryInput,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves server business failures without raw diagnostics", async () => {
    const f = fixture();
    f.request.mockRejectedValue(
      new HttpResponseError(409, {
        code: "payroll_payment_balance_changed",
        detail: "PRIVATE BANK DATA",
      }),
    );
    const result = await f.actions.confirmPaymentRecovery.execute(
      access,
      operation,
      id,
      snapshot(),
      payrollRecoveryInput,
      signal(),
    );
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "payroll_payment_balance_changed" },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
  });
  it("propagates cancellation before dispatch and after pending acquisition", async () => {
    const f = fixture(),
      cancelled = new AbortController();
    cancelled.abort();
    await expect(
      f.actions.reviewPaymentRecovery.execute(access, f.raw.assessmentId, cancelled.signal),
    ).rejects.toThrow();
    expect(f.request).not.toHaveBeenCalled();
    const pending = new AbortController();
    let resolve!: (value: unknown) => void;
    f.request.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const reading = f.actions.reviewPaymentRecovery.execute(
      access,
      f.raw.assessmentId,
      pending.signal,
    );
    pending.abort();
    resolve(f.raw);
    await expect(reading).rejects.toThrow();
  });
});
