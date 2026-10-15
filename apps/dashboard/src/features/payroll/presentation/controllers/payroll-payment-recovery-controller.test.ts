import { afterEach, assert, describe, expect, it, vi } from "vitest";
import { payrollIdentifier } from "../../../../../tests/fixtures/payroll-payment-identifiers";
import {
  payrollRecoveryInput,
  payrollRecoveryReview,
} from "../../../../../tests/fixtures/payroll-payment-recoveries";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toPayrollPaymentRecoveryReview } from "../../data/mappers/payroll-payment-recovery-mapper";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollPaymentRecoveryController } from "./payroll-payment-recovery-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  access = { companyId: company, permissions: ["payroll.pay"] },
  controllers: PayrollPaymentRecoveryController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function deferred<T>() {
  let resolve!: (value: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture() {
  const raw = payrollRecoveryReview(),
    review = toPayrollPaymentRecoveryReview(raw, company, raw.assessmentId),
    receipt = { id: payrollIdentifier(99002), version: 0 };
  const actions = {
    reviewPaymentRecovery: {
      execute: vi
        .fn<PayrollUseCases["reviewPaymentRecovery"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    confirmPaymentRecovery: {
      execute: vi
        .fn<PayrollUseCases["confirmPaymentRecovery"]["execute"]>()
        .mockImplementation(async (_, __, id) => success({ id, version: 0 })),
    },
  };
  let identifier = 99001;
  const next = vi.fn(() => payrollIdentifier(identifier++)),
    controller = new PayrollPaymentRecoveryController(actions, access, raw.assessmentId, next);
  controllers.push(controller);
  return { controller, actions, next, review, receipt };
}
async function activate(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
}
describe("payroll return ownership", () => {
  it("replaces a pending review without restoring its late denial", async () => {
    const f = fixture(),
      held = deferred<typeof f.review>();
    f.actions.reviewPaymentRecovery.execute.mockReturnValueOnce(held.promise);
    f.controller.activate();
    f.controller.activate();
    const signal = f.actions.reviewPaymentRecovery.execute.mock.calls[0]?.[2];
    expect(f.actions.reviewPaymentRecovery.execute).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(failed("access_denied"));
    await Promise.resolve();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      review: f.review,
      failure: null,
    });
  });
  it("freezes the observed balance, identifiers and input once across repeated actions", async () => {
    const f = fixture(),
      held = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.confirmPaymentRecovery.execute.mockReturnValueOnce(held.promise);
    const input = { ...payrollRecoveryInput },
      saving = f.controller.confirm(input);
    input.reason = "Changed after dispatch";
    await f.controller.confirm({ ...input, amount: "1.00" });
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.actions.confirmPaymentRecovery.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.reviewPaymentRecovery.execute).toHaveBeenCalledTimes(1);
    expect(f.next).toHaveBeenCalledTimes(2);
    const sent = f.actions.confirmPaymentRecovery.execute.mock.calls[0];
    assert(sent);
    expect(sent.slice(0, 5)).toEqual([
      access,
      payrollIdentifier(99001),
      payrollIdentifier(99002),
      {
        companyId: company,
        assessmentId: f.review.assessmentId,
        assessmentRevision: 1,
        paymentVersion: 5,
        recoverable: "900.25",
        earliestConfirmationAt: "2026-10-14T06:00:00.123456Z",
        canConfirm: true,
      },
      payrollRecoveryInput,
    ]);
    expect(Object.isFrozen(sent[3])).toBe(true);
    expect(Object.isFrozen(sent[4])).toBe(true);
    held.resolve(success(f.receipt));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      receipt: f.receipt,
      operationId: null,
    });
  });
  it.each([
    "payroll_payment_balance_changed",
    "recent_authentication_required",
    "access_denied",
    "payroll_self_recovery_forbidden",
    "request_rate_limited",
    "data_conflict",
  ])("retains the original uncertain command after %s", async (code) => {
    const f = fixture();
    await activate(f);
    f.actions.confirmPaymentRecovery.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed(code));
    await f.controller.confirm(payrollRecoveryInput);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.confirm({ ...payrollRecoveryInput, amount: "1.00" });
    await f.controller.refresh();
    expect(f.actions.reviewPaymentRecovery.execute).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    const calls = f.actions.confirmPaymentRecovery.execute.mock.calls;
    expect(calls).toHaveLength(3);
    for (const call of calls) expect(call.slice(0, 5)).toEqual(calls[0]?.slice(0, 5));
    expect(f.next).toHaveBeenCalledTimes(2);
  });
  it("requires an explicit new review after a definite balance conflict", async () => {
    const f = fixture();
    await activate(f);
    f.actions.confirmPaymentRecovery.execute.mockResolvedValueOnce(
      failed("payroll_payment_balance_changed"),
    );
    await f.controller.confirm(payrollRecoveryInput);
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.confirm(payrollRecoveryInput);
    await f.controller.retry();
    expect(f.actions.confirmPaymentRecovery.execute).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    await f.controller.confirm(payrollRecoveryInput);
    expect(f.actions.confirmPaymentRecovery.execute.mock.calls[1]?.[1]).toBe(
      payrollIdentifier(99003),
    );
    expect(f.actions.confirmPaymentRecovery.execute.mock.calls[1]?.[2]).toBe(
      payrollIdentifier(99004),
    );
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
  it.each([
    "access_denied",
    "company_access_denied",
    "payroll_self_recovery_forbidden",
    "recent_authentication_required",
  ])("removes financial evidence after %s and does not reacquire automatically", async (code) => {
    const f = fixture();
    await activate(f);
    f.actions.confirmPaymentRecovery.execute.mockResolvedValueOnce(failed(code));
    await f.controller.confirm(payrollRecoveryInput);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    await f.controller.confirm(payrollRecoveryInput);
    await f.controller.retry();
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.actions.reviewPaymentRecovery.execute).toHaveBeenCalledTimes(1);
  });
  it("allows inspection but cannot submit a server-blocked review", async () => {
    const f = fixture();
    f.actions.reviewPaymentRecovery.execute.mockResolvedValue(
      success({ ...f.review, canConfirm: false, blockers: ["BENEFICIARY"] }),
    );
    await activate(f);
    await f.controller.confirm(payrollRecoveryInput);
    expect(f.controller.getSnapshot().review?.recoveries).toHaveLength(1);
    expect(f.actions.confirmPaymentRecovery.execute).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
  });
  it("aborts a disposed command and ignores its late completion after reactivation", async () => {
    const f = fixture(),
      held = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.confirmPaymentRecovery.execute.mockReturnValueOnce(held.promise);
    const saving = f.controller.confirm(payrollRecoveryInput),
      signal = f.actions.confirmPaymentRecovery.execute.mock.calls[0]?.[5];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    await activate(f);
    held.resolve(success(f.receipt));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      receipt: null,
      operationId: null,
      review: f.review,
    });
  });
  it("does not retain a disposed read or notify an unregistered observer", async () => {
    const f = fixture(),
      held = deferred<typeof f.review>(),
      listener = vi.fn();
    const stop = f.controller.subscribe(listener);
    f.actions.reviewPaymentRecovery.execute.mockReturnValueOnce(held.promise);
    f.controller.activate();
    stop();
    const notifications = listener.mock.calls.length;
    const signal = f.actions.reviewPaymentRecovery.execute.mock.calls[0]?.[2];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(f.review));
    await Promise.resolve();
    expect(f.controller.getSnapshot().review).toBeNull();
    expect(listener).toHaveBeenCalledTimes(notifications);
  });
  it("does not assume that an unexpected exception proves the return was rejected", async () => {
    const f = fixture();
    await activate(f);
    f.actions.confirmPaymentRecovery.execute.mockRejectedValueOnce(new Error("PRIVATE BANK DATA"));
    await f.controller.confirm(payrollRecoveryInput);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
  });
});
