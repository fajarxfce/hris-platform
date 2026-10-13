import { afterEach, describe, expect, it, vi } from "vitest";
import {
  expenseIdentifier,
  expenseSubmissionRecord,
} from "../../../../../tests/fixtures/expense-claims";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpenseSubmission } from "../../data/mappers/expense-submission-mapper";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpenseReceiptController } from "./expense-receipt-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const controllers: ExpenseReceiptController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function deferred() {
  let resolve!: (value: Result<void>) => void;
  const promise = new Promise<Result<void>>((done) => {
    resolve = done;
  });
  return { resolve, promise };
}
function fixture() {
  const source = toExpenseSubmission(
    expenseSubmissionRecord(),
    company,
    expenseIdentifier(1001) as ExpenseSubmissionId,
  );
  const download = {
    execute: vi
      .fn<ExpenseUseCases["downloadReceipt"]["execute"]>()
      .mockResolvedValue(success(undefined)),
  };
  const controller = new ExpenseReceiptController(
    download,
    { companyId: company, permissions: ["expenses.read"] },
    source,
  );
  controllers.push(controller);
  return { source, download, controller, revision: source.lines[0]?.receipts[0]?.revisionId ?? "" };
}
describe("expense receipt ownership", () => {
  it("owns one known receipt, cancels explicitly and ignores a late result after retry", async () => {
    const f = fixture();
    await f.controller.open(f.revision);
    expect(f.download.execute).not.toHaveBeenCalled();
    f.controller.activate();
    await f.controller.open(expenseIdentifier(999));
    expect(f.download.execute).not.toHaveBeenCalled();
    const pending = deferred();
    f.download.execute.mockReturnValueOnce(pending.promise);
    const first = f.controller.open(f.revision);
    await f.controller.open(f.revision);
    expect(f.download.execute).toHaveBeenCalledTimes(1);
    const old = f.download.execute.mock.calls[0]?.[3];
    f.controller.cancel();
    expect(old?.aborted).toBe(true);
    await f.controller.open(f.revision);
    pending.resolve(failed("expense_receipt_not_found"));
    await first;
    expect(f.controller.getSnapshot().stage).toBe("started");
    expect(f.download.execute).toHaveBeenCalledTimes(2);
  });
  it("disposal aborts acquisition and cannot publish a late completion", async () => {
    const f = fixture(),
      pending = deferred();
    f.controller.activate();
    f.download.execute.mockReturnValueOnce(pending.promise);
    const opening = f.controller.open(f.revision),
      old = f.download.execute.mock.calls[0]?.[3];
    const listener = vi.fn(),
      unsubscribe = f.controller.subscribe(listener);
    unsubscribe();
    f.controller.deactivate();
    expect(old?.aborted).toBe(true);
    pending.resolve(success(undefined));
    await opening;
    expect(f.controller.getSnapshot().stage).toBe("idle");
    expect(listener).not.toHaveBeenCalled();
  });
  it.each([
    "access_denied",
    "company_access_denied",
    "expense_receipt_not_found",
    "mfa_required",
    "company_module_disabled",
  ])("requires a fresh source after %s", async (code) => {
    const f = fixture();
    f.controller.activate();
    f.download.execute.mockResolvedValueOnce(failed(code));
    await f.controller.open(f.revision);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", failure: { code } });
    await f.controller.open(f.revision);
    expect(f.download.execute).toHaveBeenCalledTimes(1);
  });
  it("retains explicit retry after a transport failure without retrying automatically", async () => {
    const f = fixture();
    f.controller.activate();
    f.download.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await f.controller.open(f.revision);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "failed",
      failure: { code: "connection_unavailable" },
    });
    expect(f.download.execute).toHaveBeenCalledTimes(1);
    await f.controller.open(f.revision);
    expect(f.controller.getSnapshot().stage).toBe("started");
  });
  it("contains unexpected failures without exposing technical text or leaving a stuck operation", async () => {
    const f = fixture();
    f.controller.activate();
    f.download.execute.mockRejectedValueOnce(new Error("PRIVATE TECHNICAL TEXT"));
    await f.controller.open(f.revision);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "failed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.open(f.revision);
    expect(f.controller.getSnapshot().stage).toBe("started");
  });
});
