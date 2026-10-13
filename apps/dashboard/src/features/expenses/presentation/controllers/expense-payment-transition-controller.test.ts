import { afterEach, assert, describe, expect, it, vi } from "vitest";
import { expenseIdentifier } from "../../../../../tests/fixtures/expense-claims";
import { expensePaymentRecord } from "../../../../../tests/fixtures/expense-payments";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpensePaymentReview } from "../../data/mappers/expense-payment-review-mapper";
import type { ExpensePaymentBatchId } from "../../domain/entities/expense-payment-batch";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePaymentTransitionController } from "./expense-payment-transition-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.pay"] };
const controllers: ExpensePaymentTransitionController[] = [];
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
  const raw = expensePaymentRecord({ status: "PREPARED" }),
    review = toExpensePaymentReview(raw, company, raw.batch.id as ExpensePaymentBatchId, null),
    receipt = { id: review.batch.id, version: 1 };
  const actions = {
    reviewPaymentTransition: {
      execute: vi
        .fn<ExpenseUseCases["reviewPaymentTransition"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    transitionPayment: {
      execute: vi
        .fn<ExpenseUseCases["transitionPayment"]["execute"]>()
        .mockResolvedValue(success(receipt)),
    },
  };
  const next = vi
    .fn()
    .mockReturnValueOnce(expenseIdentifier(901))
    .mockReturnValue(expenseIdentifier(902));
  const controller = new ExpensePaymentTransitionController(
    actions,
    access,
    review.batch.id,
    "RELEASE",
    next,
  );
  controllers.push(controller);
  return { controller, actions, next, review, receipt };
}
async function activate(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
}
describe("payment transition ownership", () => {
  it("replaces a pending review and ignores its late denial", async () => {
    const f = fixture(),
      held = deferred<typeof f.review>();
    f.actions.reviewPaymentTransition.execute.mockReturnValueOnce(held.promise);
    f.controller.activate();
    f.controller.activate();
    expect(f.actions.reviewPaymentTransition.execute).toHaveBeenCalledTimes(1);
    const signal = f.actions.reviewPaymentTransition.execute.mock.calls[0]?.[3];
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
  it("captures one command while repeated presses, refresh and changed input are ignored", async () => {
    const f = fixture(),
      held = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.transitionPayment.execute.mockReturnValueOnce(held.promise);
    const pending = f.controller.confirm("Original reason");
    await f.controller.confirm("Changed");
    await f.controller.refresh();
    await f.controller.retry();
    expect(f.actions.transitionPayment.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.reviewPaymentTransition.execute).toHaveBeenCalledTimes(1);
    expect(f.next).toHaveBeenCalledTimes(1);
    const sent = f.actions.transitionPayment.execute.mock.calls[0];
    assert(sent);
    expect(sent.slice(0, 5)).toEqual([
      access,
      expenseIdentifier(901),
      {
        id: f.review.batch.id,
        companyId: company,
        status: "PREPARED",
        version: 0,
        availableActions: ["RELEASE", "CANCEL"],
      },
      "RELEASE",
      "Original reason",
    ]);
    expect(Object.isFrozen(sent[2])).toBe(true);
    expect(Object.isFrozen(sent[2].availableActions)).toBe(true);
    held.resolve(success(f.receipt));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      receipt: f.receipt,
    });
  });
  it.each([
    "stale_version",
    "recent_authentication_required",
    "access_denied",
    "request_rate_limited",
    "independent_payment_release_required",
  ])("retains the first operation after response loss followed by %s", async (code) => {
    const f = fixture();
    await activate(f);
    f.actions.transitionPayment.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed(code));
    await f.controller.confirm("Original reason");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.refresh();
    expect(f.actions.reviewPaymentTransition.execute).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    const calls = f.actions.transitionPayment.execute.mock.calls;
    expect(calls.map((call) => call.slice(0, 5))).toEqual([
      calls[0]?.slice(0, 5),
      calls[0]?.slice(0, 5),
      calls[0]?.slice(0, 5),
    ]);
    expect(f.next).toHaveBeenCalledTimes(1);
  });
  it("requires fresh review after a definite conflict before issuing a new operation", async () => {
    const f = fixture();
    await activate(f);
    f.actions.transitionPayment.execute.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.confirm("First");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.confirm("Second");
    await f.controller.retry();
    expect(f.next).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    await f.controller.confirm("Second");
    expect(f.actions.transitionPayment.execute.mock.calls[1]?.[1]).toBe(expenseIdentifier(902));
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
  it("clears bank data after a known access rejection and waits for another review", async () => {
    const f = fixture();
    await activate(f);
    f.actions.transitionPayment.execute.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.confirm("Reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    await f.controller.confirm("Again");
    expect(f.next).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
  });
  it("keeps inspection local without refetching or selecting an unrelated payment", async () => {
    const f = fixture();
    await activate(f);
    const item = f.review.batch.items[0];
    assert(item);
    f.controller.selectItem(item.id);
    expect(f.controller.getSnapshot().selectedItemId).toBe(item.id);
    f.controller.selectItem(expenseIdentifier(9999));
    expect(f.controller.getSnapshot().selectedItemId).toBe(item.id);
    f.controller.selectItem(null);
    expect(f.controller.getSnapshot().selectedItemId).toBeNull();
    expect(f.actions.reviewPaymentTransition.execute).toHaveBeenCalledTimes(1);
  });
  it("aborts an abandoned write and cannot let its completion overwrite a replacement review", async () => {
    const f = fixture(),
      held = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.transitionPayment.execute.mockReturnValueOnce(held.promise);
    const pending = f.controller.confirm("Original"),
      signal = f.actions.transitionPayment.execute.mock.calls[0]?.[5];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    await activate(f);
    held.resolve(success(f.receipt));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      receipt: null,
      operationId: null,
    });
  });
  it("does not restore data from a disposed read", async () => {
    const f = fixture(),
      held = deferred<typeof f.review>();
    f.actions.reviewPaymentTransition.execute.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const signal = f.actions.reviewPaymentTransition.execute.mock.calls[0]?.[3];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(f.review));
    await Promise.resolve();
    expect(f.controller.getSnapshot().review).toBeNull();
  });
  it("does not infer rejection from an unexpected command exception", async () => {
    const f = fixture();
    await activate(f);
    f.actions.transitionPayment.execute.mockRejectedValueOnce(new Error("PRIVATE"));
    await f.controller.confirm("Reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(1);
  });
});
