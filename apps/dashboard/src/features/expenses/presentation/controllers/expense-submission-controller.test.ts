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
function deferred() {
  let resolve!: (value: Result<ExpenseSubmission>) => void;
  const promise = new Promise<Result<ExpenseSubmission>>((done) => {
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
  const controller = new ExpenseSubmissionController(load, access, value.id);
  controllers.push(controller);
  return { value, load, controller };
}
async function activate(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
}
describe("expense submission lifecycle", () => {
  it("activates once, ignores late replaced reads and releases disposed data", async () => {
    const f = fixture(),
      pending = deferred();
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
  it("clears stale evidence during refresh and allows explicit recovery after failure", async () => {
    const f = fixture();
    await activate(f);
    const pending = deferred();
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
  it("revokes only the exact observed source and requires a new review", async () => {
    const f = fixture();
    await activate(f);
    const failure = { code: "expense_receipt_not_found", fields: {}, parameters: {} };
    f.controller.revokeEvidence(current(), failure);
    expect(f.controller.getSnapshot().stage).toBe("ready");
    f.controller.revokeEvidence(f.value, failure);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      submission: null,
      failure,
    });
    const replacement = current();
    f.load.execute.mockResolvedValueOnce(success(replacement));
    await f.controller.refresh();
    f.controller.revokeEvidence(f.value, failure);
    expect(f.controller.getSnapshot().submission).toBe(replacement);
  });
  it("does not let a disposed read restore evidence", async () => {
    const f = fixture(),
      pending = deferred();
    f.load.execute.mockReturnValueOnce(pending.promise);
    f.controller.activate();
    const signal = f.load.execute.mock.calls[0]?.[2];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    pending.resolve(success(f.value));
    await Promise.resolve();
    expect(f.controller.getSnapshot().submission).toBeNull();
  });
  it("contains unexpected read failures and recovers without exposing technical text", async () => {
    const f = fixture();
    f.load.execute.mockRejectedValueOnce(new Error("PRIVATE TECHNICAL TEXT"));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.refresh();
    expect(f.controller.getSnapshot().submission).toBe(f.value);
  });
});
