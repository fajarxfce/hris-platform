import { assert, describe, expect, it, vi } from "vitest";
import {
  expensePaymentRecord,
  expensePaymentReviewPage,
} from "../../../../../tests/fixtures/expense-payments";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpensePaymentReview } from "../../data/mappers/expense-payment-review-mapper";
import type { ExpensePaymentBatchId } from "../../domain/entities/expense-payment-batch";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePaymentDownloadController } from "./expense-payment-download-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
function deferred() {
  let resolve!: (result: Result<void>) => void;
  const promise = new Promise<Result<void>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture(settled = 0) {
  const raw = expensePaymentRecord({ settled });
  const review = toExpensePaymentReview(
    expensePaymentReviewPage(raw),
    company,
    raw.batch.id as ExpensePaymentBatchId,
    null,
  );
  const execute = vi
    .fn<ExpenseUseCases["downloadPayment"]["execute"]>()
    .mockResolvedValue(success(undefined));
  const controller = new ExpensePaymentDownloadController(
    { execute },
    { companyId: company, permissions: ["expenses.pay"] },
    review,
  );
  controller.activate();
  return { execute, controller, review };
}
describe("payment worksheet ownership", () => {
  it("allows one download at a time and keeps the reviewed batch", async () => {
    const f = fixture(),
      held = deferred();
    f.execute.mockReturnValueOnce(held.promise);
    const download = f.controller.open();
    await f.controller.open();
    expect(f.execute).toHaveBeenCalledTimes(1);
    expect(f.execute.mock.calls[0]?.[1]).toBe(f.review);
    expect(f.controller.getSnapshot().stage).toBe("downloading");
    held.resolve(success(undefined));
    await download;
    expect(f.controller.getSnapshot().stage).toBe("started");
  });
  it("cancels old work without allowing its finalizer to release a new download", async () => {
    const f = fixture(),
      old = deferred(),
      next = deferred();
    f.execute.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise);
    const first = f.controller.open(),
      signal = f.execute.mock.calls[0]?.[2];
    f.controller.cancel();
    expect(signal?.aborted).toBe(true);
    const second = f.controller.open();
    old.resolve(failed("access_denied"));
    await first;
    expect(f.controller.getSnapshot().stage).toBe("downloading");
    await f.controller.open();
    expect(f.execute).toHaveBeenCalledTimes(2);
    next.resolve(success(undefined));
    await second;
    expect(f.controller.getSnapshot().stage).toBe("started");
  });
  it.each([
    "access_denied",
    "recent_authentication_required",
    "expense_payment_instructions_unavailable",
    "expense_payment_batch_not_found",
  ])("requires a new review after %s", async (code) => {
    const f = fixture();
    f.execute.mockResolvedValue(failed(code));
    await f.controller.open();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", failure: { code } });
    await f.controller.open();
    expect(f.execute).toHaveBeenCalledTimes(1);
  });
  it("allows an explicit retry after a transient transport failure", async () => {
    const f = fixture();
    f.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await f.controller.open();
    expect(f.controller.getSnapshot().stage).toBe("failed");
    await f.controller.open();
    expect(f.controller.getSnapshot().stage).toBe("started");
    expect(f.execute).toHaveBeenCalledTimes(2);
  });
  it("cleans up on departure and ignores late completion", async () => {
    const f = fixture(),
      held = deferred();
    f.execute.mockReturnValueOnce(held.promise);
    const listener = vi.fn(),
      unsubscribe = f.controller.subscribe(listener);
    const download = f.controller.open(),
      signal = f.execute.mock.calls[0]?.[2];
    f.controller.deactivate();
    unsubscribe();
    listener.mockClear();
    assert(signal);
    expect(signal.aborted).toBe(true);
    held.resolve(success(undefined));
    await download;
    expect(f.controller.getSnapshot().stage).toBe("idle");
    expect(listener).not.toHaveBeenCalled();
    await f.controller.open();
    expect(f.execute).toHaveBeenCalledTimes(1);
  });
  it("survives activation cleanup without leaking an old failure into replacement work", async () => {
    const f = fixture(),
      old = deferred();
    f.execute.mockReturnValueOnce(old.promise);
    const pending = f.controller.open();
    f.controller.deactivate();
    f.controller.activate();
    await f.controller.open();
    old.resolve(failed("recent_authentication_required"));
    await pending;
    expect(f.controller.getSnapshot().stage).toBe("started");
  });
  it("does not start without export availability", async () => {
    const f = fixture(1);
    await f.controller.open();
    expect(f.execute).not.toHaveBeenCalled();
  });
  it("sanitizes unexpected errors and releases its pending operation", async () => {
    const f = fixture();
    f.execute.mockRejectedValueOnce(new Error("PRIVATE"));
    await f.controller.open();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "failed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.open();
    expect(f.controller.getSnapshot().stage).toBe("started");
  });
});
