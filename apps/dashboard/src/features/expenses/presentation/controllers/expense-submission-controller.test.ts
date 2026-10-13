import { afterEach, describe, expect, it, vi } from "vitest";
import {
  expenseIdentifier,
  expenseSubmissionRecord,
} from "../../../../../tests/fixtures/expense-claims";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpenseSubmission } from "../../data/mappers/expense-submission-mapper";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpenseSubmissionController } from "./expense-submission-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.read"] };
const current = () =>
  toExpenseSubmission(
    expenseSubmissionRecord(),
    company,
    expenseIdentifier(1001) as ExpenseSubmissionId,
  );
const controllers: ExpenseSubmissionController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function deferred<T>() {
  let resolve!: (value: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { resolve, promise };
}
function fixture() {
  const value = current();
  const load = {
    execute: vi
      .fn<ExpenseUseCases["loadSubmission"]["execute"]>()
      .mockResolvedValue(success(value)),
  };
  const receipt = {
    execute: vi
      .fn<ExpenseUseCases["downloadReceipt"]["execute"]>()
      .mockResolvedValue(success(undefined)),
  };
  const controller = new ExpenseSubmissionController(load, receipt, access, value.id);
  controllers.push(controller);
  const revision = value.lines[0]?.receipts[0]?.revisionId ?? "";
  return { value, load, receipt, controller, revision };
}
async function activate(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
}
describe("expense submission lifecycle", () => {
  it("activates once, ignores late replaced reads and releases disposed data", async () => {
    const f = fixture(),
      pending = deferred<ExpenseSubmission>();
    f.load.execute.mockReturnValueOnce(pending.promise);
    f.controller.activate();
    f.controller.activate();
    expect(f.load.execute).toHaveBeenCalledTimes(1);
    const old = f.load.execute.mock.calls[0]?.[2];
    await f.controller.refresh();
    expect(old?.aborted).toBe(true);
    expect(f.controller.getSnapshot().submission).toBe(f.value);
    pending.resolve(failed("mfa_required"));
    await Promise.resolve();
    expect(f.controller.getSnapshot().failure).toBeNull();
    const listener = vi.fn(),
      unsubscribe = f.controller.subscribe(listener);
    unsubscribe();
    f.controller.deactivate();
    expect(listener).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot().submission).toBeNull();
  });
  it("does not retain previous evidence while a failed refresh is waiting for recovery", async () => {
    const f = fixture();
    await activate(f);
    const pending = deferred<ExpenseSubmission>();
    f.load.execute.mockReturnValueOnce(pending.promise);
    const refreshing = f.controller.refresh();
    expect(f.controller.getSnapshot().submission).toBeNull();
    pending.resolve(failed("expense_submission_not_found"));
    await refreshing;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", submission: null });
    expect(f.load.execute).toHaveBeenCalledTimes(2);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().submission).toBe(f.value);
  });
  it("owns one download, cancels it explicitly and ignores its late outcome after retry", async () => {
    const f = fixture();
    await activate(f);
    const pending = deferred<void>();
    f.receipt.execute.mockReturnValueOnce(pending.promise);
    const original = f.controller.downloadReceipt(f.revision);
    await f.controller.downloadReceipt(f.revision);
    expect(f.receipt.execute).toHaveBeenCalledTimes(1);
    const old = f.receipt.execute.mock.calls[0]?.[3];
    f.controller.cancelDownload();
    expect(old?.aborted).toBe(true);
    await f.controller.downloadReceipt(f.revision);
    expect(f.controller.getSnapshot().download.stage).toBe("started");
    pending.resolve(failed("expense_receipt_not_found"));
    await original;
    expect(f.controller.getSnapshot().stage).toBe("ready");
    expect(f.controller.getSnapshot().download.stage).toBe("started");
    expect(f.receipt.execute).toHaveBeenCalledTimes(2);
  });
  it.each(["deactivate", "refresh"] as const)(
    "cancels receipt acquisition on %s and rejects late completion",
    async (action) => {
      const f = fixture();
      await activate(f);
      const pending = deferred<void>();
      f.receipt.execute.mockReturnValueOnce(pending.promise);
      const download = f.controller.downloadReceipt(f.revision),
        old = f.receipt.execute.mock.calls[0]?.[3];
      await f.controller[action]();
      expect(old?.aborted).toBe(true);
      pending.resolve(success(undefined));
      await download;
      expect(f.controller.getSnapshot().download.stage).toBe("idle");
      expect(f.controller.getSnapshot().submission).toBe(action === "deactivate" ? null : f.value);
    },
  );
  it.each([
    "access_denied",
    "company_access_denied",
    "expense_receipt_not_found",
    "mfa_required",
    "company_module_disabled",
  ])("clears evidence after receipt authorization fails with %s", async (code) => {
    const f = fixture();
    await activate(f);
    f.receipt.execute.mockResolvedValueOnce(failed(code));
    await f.controller.downloadReceipt(f.revision);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      submission: null,
      failure: { code },
    });
    await f.controller.downloadReceipt(f.revision);
    expect(f.receipt.execute).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("ready");
  });
  it("retains readable evidence on a transient download failure and does not retry automatically", async () => {
    const f = fixture();
    await activate(f);
    f.receipt.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await f.controller.downloadReceipt(f.revision);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      submission: f.value,
      download: { stage: "failed", failure: { code: "connection_unavailable" } },
    });
    expect(f.receipt.execute).toHaveBeenCalledTimes(1);
    await f.controller.downloadReceipt(f.revision);
    expect(f.controller.getSnapshot().download.stage).toBe("started");
    await f.controller.downloadReceipt(expenseIdentifier(999));
    expect(f.receipt.execute).toHaveBeenCalledTimes(2);
  });
  it("contains unexpected failures without exposing technical text or leaving a stuck operation", async () => {
    const f = fixture();
    await activate(f);
    f.receipt.execute.mockRejectedValueOnce(new Error("PRIVATE TECHNICAL TEXT"));
    await f.controller.downloadReceipt(f.revision);
    expect(f.controller.getSnapshot().download).toMatchObject({
      stage: "failed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.downloadReceipt(f.revision);
    expect(f.controller.getSnapshot().download.stage).toBe("started");
  });
});
