import { afterEach, assert, describe, expect, it, vi } from "vitest";
import { payrollIdentifier } from "../../../../../tests/fixtures/payroll-payment-identifiers";
import { payrollPaymentRecord } from "../../../../../tests/fixtures/payroll-payments";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toPayrollPaymentReview } from "../../data/mappers/payroll-payment-review-mapper";
import type { PayrollPaymentBatchId } from "../../domain/entities/payroll-payment-batch";
import type { PayrollPaymentOutcome } from "../../domain/entities/payroll-payment-outcome";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollPaymentReconciliationController } from "./payroll-payment-reconciliation-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["payroll.pay"] };
const controllers: PayrollPaymentReconciliationController[] = [];
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
  const raw = payrollPaymentRecord(),
    review = toPayrollPaymentReview(raw, company, raw.batch.id as PayrollPaymentBatchId, null),
    receipt = { id: review.batch.id, version: 2 };
  const actions = {
    reviewReconciliation: {
      execute: vi
        .fn<PayrollUseCases["reviewReconciliation"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    reconcilePayment: {
      execute: vi
        .fn<PayrollUseCases["reconcilePayment"]["execute"]>()
        .mockResolvedValue(success(receipt)),
    },
  };
  const next = vi
    .fn()
    .mockReturnValueOnce(payrollIdentifier(901))
    .mockReturnValue(payrollIdentifier(902));
  const controller = new PayrollPaymentReconciliationController(
    actions,
    access,
    review.batch.id,
    next,
  );
  controllers.push(controller);
  const outcomes: PayrollPaymentOutcome[] = review.batch.items.map((item, index) => ({
    itemId: item.id,
    status: "SUCCEEDED",
    transactionReference: `BANK-${index + 1}`,
    occurredAt: "2026-10-13T06:01:00Z",
    reason: "Bank confirmed",
    confirmedNoTransfer: false,
  }));
  const enter = (index = 0) => {
    const outcome = outcomes[index];
    assert(outcome);
    controller.selectItem(outcome.itemId);
    controller.saveOutcome(outcome);
  };
  return { controller, actions, next, review, receipt, outcomes, enter };
}
async function activate(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
}
describe("payroll reconciliation ownership", () => {
  it("owns current panel dirtiness without accepting stale panel updates or submitting uncommitted input", async () => {
    const f = fixture();
    await activate(f);
    f.enter();
    const first = f.outcomes[0],
      second = f.outcomes[1];
    assert(first && second);
    f.controller.selectItem(first.itemId);
    f.controller.setEntryDirty(first.itemId, true);
    expect(f.controller.getSnapshot().entryDirty).toBe(true);
    await f.controller.confirm("Panel changes are uncommitted");
    expect(f.actions.reconcilePayment.execute).not.toHaveBeenCalled();
    f.controller.selectItem(second.itemId);
    expect(f.controller.getSnapshot().entryDirty).toBe(false);
    f.controller.setEntryDirty(first.itemId, true);
    expect(f.controller.getSnapshot().entryDirty).toBe(false);
    f.controller.setEntryDirty(second.itemId, true);
    f.controller.selectItem(null);
    expect(f.controller.getSnapshot().entryDirty).toBe(false);
    f.controller.setEntryDirty(second.itemId, true);
    expect(f.controller.getSnapshot().entryDirty).toBe(false);
  });
  it("stages, replaces and removes results without writes or extra acquisition", async () => {
    const f = fixture();
    await activate(f);
    const first = f.outcomes[0];
    assert(first);
    f.enter();
    f.enter(1);
    expect(f.controller.getSnapshot().results).toHaveLength(2);
    f.controller.selectItem(first.itemId);
    f.controller.saveOutcome({ ...first, transactionReference: "CORRECTED" });
    expect(
      f.controller.getSnapshot().results.find((item) => item.itemId === first.itemId)
        ?.transactionReference,
    ).toBe("CORRECTED");
    expect(f.controller.getSnapshot().selectedItemId).toBeNull();
    f.controller.selectItem(first.itemId);
    f.controller.clearOutcome();
    expect(f.controller.getSnapshot().results.map((item) => item.itemId)).toEqual([
      f.outcomes[1]?.itemId,
    ]);
    expect(f.actions.reviewReconciliation.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.reconcilePayment.execute).not.toHaveBeenCalled();
  });
  it("does not stage another row, an invalid result or submit an empty collection", async () => {
    const f = fixture();
    await activate(f);
    const first = f.outcomes[0],
      second = f.outcomes[1];
    assert(first && second);
    f.controller.selectItem(payrollIdentifier(9999));
    expect(f.controller.getSnapshot().selectedItemId).toBeNull();
    f.controller.selectItem(first.itemId);
    f.controller.saveOutcome(second);
    expect(f.controller.getSnapshot().results).toHaveLength(0);
    f.controller.saveOutcome({
      ...first,
      status: "FAILED",
      transactionReference: null,
      confirmedNoTransfer: false,
    });
    expect(f.controller.getSnapshot().entryFailure?.code).toBe("invalid_payroll_payment_result");
    expect(f.controller.getSnapshot().selectedItemId).toBe(first.itemId);
    await f.controller.confirm("No results");
    expect(f.actions.reconcilePayment.execute).not.toHaveBeenCalled();
    f.controller.selectItem(null);
    expect(f.controller.getSnapshot().entryFailure).toBeNull();
  });
  it("replaces pending reviews and ignores a stale denial", async () => {
    const f = fixture(),
      held = deferred<typeof f.review>();
    f.actions.reviewReconciliation.execute.mockReturnValueOnce(held.promise);
    f.controller.activate();
    f.controller.activate();
    expect(f.actions.reviewReconciliation.execute).toHaveBeenCalledTimes(1);
    const signal = f.actions.reviewReconciliation.execute.mock.calls[0]?.[2];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(failed("access_denied"));
    await Promise.resolve();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      review: f.review,
      failure: null,
    });
  });
  it("captures one immutable command while repeated actions and input changes are ignored", async () => {
    const f = fixture(),
      held = deferred<typeof f.receipt>();
    await activate(f);
    f.enter();
    f.enter(1);
    f.actions.reconcilePayment.execute.mockReturnValueOnce(held.promise);
    const pending = f.controller.confirm("Original note");
    await f.controller.confirm("Changed note");
    await f.controller.refresh();
    await f.controller.retry();
    f.enter(2);
    expect(f.actions.reconcilePayment.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.reviewReconciliation.execute).toHaveBeenCalledTimes(1);
    const sent = f.actions.reconcilePayment.execute.mock.calls[0];
    assert(sent);
    expect(sent[0]).toEqual(access);
    expect(sent[1]).toBe(payrollIdentifier(901));
    expect(sent[2]).toMatchObject({
      status: "RELEASED",
      version: 1,
      pendingItemIds: f.outcomes.map((item) => item.itemId),
    });
    expect(sent[3]).toEqual(f.outcomes.slice(0, 2));
    expect(sent[4]).toBe("Original note");
    expect(Object.isFrozen(sent[2].pendingItemIds)).toBe(true);
    expect(Object.isFrozen(sent[3])).toBe(true);
    expect(Object.isFrozen(sent[3][0])).toBe(true);
    held.resolve(success(f.receipt));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      results: [],
      receipt: f.receipt,
    });
    expect(f.next).toHaveBeenCalledTimes(1);
  });
  it.each([
    "stale_version",
    "mfa_required",
    "access_denied",
    "data_conflict",
    "payroll_payment_item_not_pending",
    "access_denied",
  ])("retains original outcomes after response loss followed by %s", async (code) => {
    const f = fixture();
    await activate(f);
    f.enter();
    f.enter(1);
    f.actions.reconcilePayment.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed(code));
    await f.controller.confirm("Original note");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    f.enter(2);
    await f.controller.confirm("Changed");
    await f.controller.refresh();
    expect(f.actions.reviewReconciliation.execute).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    const calls = f.actions.reconcilePayment.execute.mock.calls;
    expect(calls).toHaveLength(3);
    for (const call of calls) expect(call.slice(0, 5)).toEqual(calls[0]?.slice(0, 5));
    expect(f.next).toHaveBeenCalledTimes(1);
  });
  it("requires a fresh review and explicit re-entry after a definite conflict", async () => {
    const f = fixture();
    await activate(f);
    f.enter();
    f.actions.reconcilePayment.execute.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.confirm("First");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    f.enter(1);
    await f.controller.confirm("Second");
    await f.controller.retry();
    expect(f.next).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().results).toHaveLength(0);
    await f.controller.confirm("Second");
    expect(f.next).toHaveBeenCalledTimes(1);
    f.enter(1);
    await f.controller.confirm("Second");
    expect(f.actions.reconcilePayment.execute.mock.calls[1]?.[1]).toBe(payrollIdentifier(902));
    expect(f.actions.reconcilePayment.execute.mock.calls[1]?.[3]).toEqual([f.outcomes[1]]);
  });
  it("keeps results editable after definite input rejection", async () => {
    const f = fixture();
    await activate(f);
    f.enter();
    f.actions.reconcilePayment.execute.mockResolvedValueOnce(
      failed("duplicate_payment_transaction_reference"),
    );
    await f.controller.confirm("Checked");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      operationId: null,
      results: [f.outcomes[0]],
    });
    const first = f.outcomes[0];
    assert(first);
    f.controller.selectItem(first.itemId);
    f.controller.saveOutcome({ ...first, transactionReference: "CORRECTED" });
    await f.controller.confirm("Corrected");
    expect(f.actions.reconcilePayment.execute.mock.calls[1]?.[3][0]?.transactionReference).toBe(
      "CORRECTED",
    );
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
  it("clears visible bank evidence after definite access rejection", async () => {
    const f = fixture();
    await activate(f);
    f.enter();
    f.actions.reconcilePayment.execute.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.confirm("Checked");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      results: [],
      selectedItemId: null,
      operationId: null,
    });
    await f.controller.confirm("Again");
    expect(f.next).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
  });
  it("aborts abandoned writes and ignores their late completion after reactivation", async () => {
    const f = fixture(),
      held = deferred<typeof f.receipt>();
    await activate(f);
    f.enter();
    f.actions.reconcilePayment.execute.mockReturnValueOnce(held.promise);
    const pending = f.controller.confirm("Original"),
      signal = f.actions.reconcilePayment.execute.mock.calls[0]?.[5];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    await activate(f);
    held.resolve(success(f.receipt));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      results: [],
      receipt: null,
      operationId: null,
    });
  });
  it("does not restore disposed reads or publish into removed subscribers", async () => {
    const f = fixture(),
      held = deferred<typeof f.review>(),
      changed = vi.fn();
    const unsubscribe = f.controller.subscribe(changed);
    f.actions.reviewReconciliation.execute.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const signal = f.actions.reviewReconciliation.execute.mock.calls[0]?.[2];
    unsubscribe();
    changed.mockClear();
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(f.review));
    await Promise.resolve();
    expect(f.controller.getSnapshot().review).toBeNull();
    expect(changed).not.toHaveBeenCalled();
  });
  it("does not infer rejection from an unexpected command exception", async () => {
    const f = fixture();
    await activate(f);
    f.enter();
    f.actions.reconcilePayment.execute.mockRejectedValueOnce(new Error("PRIVATE"));
    await f.controller.confirm("Checked");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
});
