import { afterEach, assert, describe, expect, it, vi } from "vitest";
import {
  expenseIdentifier,
  expenseSubmissionRecord,
} from "../../../../../tests/fixtures/expense-claims";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpenseSubmission } from "../../data/mappers/expense-submission-mapper";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpenseDecisionController } from "./expense-decision-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.approve"] };
const controllers: ExpenseDecisionController[] = [];
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
function submission() {
  const raw = expenseSubmissionRecord();
  return toExpenseSubmission(raw, company, raw.id as ExpenseSubmissionId);
}
function fixture() {
  const review = submission();
  const receipt = { id: review.claimId, version: review.claimVersion + 1 };
  const actions = {
    reviewDecision: {
      execute: vi
        .fn<ExpenseUseCases["reviewDecision"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    decideSubmission: {
      execute: vi
        .fn<ExpenseUseCases["decideSubmission"]["execute"]>()
        .mockResolvedValue(success(receipt)),
    },
  };
  const nextIdentifier = vi
    .fn()
    .mockReturnValueOnce(expenseIdentifier(7001))
    .mockReturnValue(expenseIdentifier(7002));
  const controller = new ExpenseDecisionController(
    actions,
    access,
    review.id,
    "RETURN",
    nextIdentifier,
  );
  controllers.push(controller);
  return { review, receipt, actions, controller, nextIdentifier };
}
async function activate(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
}

describe("expense decision ownership", () => {
  it("activates once, replaces pending reads and ignores a late denial", async () => {
    const f = fixture(),
      first = deferred<typeof f.review>();
    f.actions.reviewDecision.execute.mockReturnValueOnce(first.promise);
    f.controller.activate();
    f.controller.activate();
    expect(f.actions.reviewDecision.execute).toHaveBeenCalledTimes(1);
    const originalSignal = f.actions.reviewDecision.execute.mock.calls[0]?.[2];
    await f.controller.refresh();
    expect(originalSignal?.aborted).toBe(true);
    first.resolve(failed("mfa_required"));
    await Promise.resolve();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      review: f.review,
      failure: null,
    });
  });

  it("retains one immutable command despite repeated presses, refresh and changing form input", async () => {
    const f = fixture(),
      pending = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.decideSubmission.execute.mockReturnValueOnce(pending.promise);
    const saving = f.controller.confirm("Original explanation", false);
    await f.controller.confirm("Changed explanation", true);
    await f.controller.retry();
    await f.controller.refresh();
    expect(f.actions.reviewDecision.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.decideSubmission.execute).toHaveBeenCalledTimes(1);
    expect(f.nextIdentifier).toHaveBeenCalledTimes(1);
    const sent = f.actions.decideSubmission.execute.mock.calls[0];
    assert(sent);
    expect(sent.slice(0, 6)).toEqual([
      access,
      expenseIdentifier(7001),
      expect.objectContaining({
        id: f.review.id,
        claimId: f.review.claimId,
        claimVersion: f.review.claimVersion,
        approvalVersion: f.review.approval.version,
        availableActions: ["DECIDE"],
      }),
      "RETURN",
      "Original explanation",
      false,
    ]);
    expect(Object.isFrozen(sent[2])).toBe(true);
    expect(Object.isFrozen(sent[2].availableActions)).toBe(true);
    expect(Object.isFrozen(sent[2].duplicateReceiptDigests)).toBe(true);
    pending.resolve(success(f.receipt));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      receipt: f.receipt,
      operationId: null,
    });
  });

  it("retries a lost response using the original operation, revisions and explanation", async () => {
    const f = fixture();
    await activate(f);
    f.actions.decideSubmission.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await f.controller.confirm("Check the original evidence", true);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      operationId: expenseIdentifier(7001),
    });
    await f.controller.confirm("Replacement", false);
    await f.controller.refresh();
    expect(f.actions.decideSubmission.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.reviewDecision.execute).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    const [first, retry] = f.actions.decideSubmission.execute.mock.calls;
    assert(first);
    assert(retry);
    expect(retry.slice(0, 6)).toEqual(first.slice(0, 6));
    expect(retry[2]).toBe(first[2]);
    expect(retry[6]).not.toBe(first[6]);
    expect(f.nextIdentifier).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "saved", receipt: f.receipt });
  });

  it.each(["stale_version", "not_assigned_approver", "mfa_required", "request_rate_limited"])(
    "does not mistake a later %s rejection for the outcome of an unconfirmed original command",
    async (code) => {
      const f = fixture();
      await activate(f);
      f.actions.decideSubmission.execute
        .mockResolvedValueOnce(failed("connection_unavailable"))
        .mockResolvedValueOnce(failed(code));
      await f.controller.confirm("Preserved reason", false);
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: expenseIdentifier(7001),
        failure: { code },
      });
      if (["mfa_required", "not_assigned_approver"].includes(code))
        expect(f.controller.getSnapshot().review).toBeNull();
      await f.controller.refresh();
      expect(f.actions.reviewDecision.execute).toHaveBeenCalledTimes(1);
      await f.controller.retry();
      const calls = f.actions.decideSubmission.execute.mock.calls;
      expect(calls).toHaveLength(3);
      expect(calls[2]?.slice(0, 6)).toEqual(calls[0]?.slice(0, 6));
      expect(f.controller.getSnapshot().stage).toBe("saved");
    },
  );

  it("requires an explicit fresh review after a confirmed version conflict", async () => {
    const f = fixture();
    await activate(f);
    f.actions.decideSubmission.execute.mockResolvedValueOnce(failed("approval_changed"));
    await f.controller.confirm("First reviewed version", false);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "conflict",
      operationId: null,
      failure: { code: "approval_changed" },
    });
    await f.controller.confirm("Cannot send stale facts", false);
    await f.controller.retry();
    expect(f.actions.decideSubmission.execute).toHaveBeenCalledTimes(1);
    const updated = {
      ...f.review,
      claimVersion: f.review.claimVersion + 1,
      approval: { ...f.review.approval, version: f.review.approval.version + 2 },
    };
    f.actions.reviewDecision.execute.mockResolvedValueOnce(success(updated));
    await f.controller.refresh();
    await f.controller.confirm("New reviewed evidence", false);
    const second = f.actions.decideSubmission.execute.mock.calls[1];
    assert(second);
    expect(second[1]).toBe(expenseIdentifier(7002));
    expect(second[2]).toMatchObject({
      claimVersion: updated.claimVersion,
      approvalVersion: updated.approval.version,
    });
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });

  it("clears evidence after a definitive access denial and cannot submit until a fresh review succeeds", async () => {
    const f = fixture();
    await activate(f);
    f.actions.decideSubmission.execute.mockResolvedValueOnce(failed("self_approval_denied"));
    await f.controller.confirm("Reviewed", false);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    await f.controller.confirm("Repeat", false);
    await f.controller.retry();
    expect(f.actions.decideSubmission.execute).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().review).toBe(f.review);
  });

  it("allows corrected form input after a definite validation rejection", async () => {
    const f = fixture();
    await activate(f);
    f.actions.decideSubmission.execute.mockResolvedValueOnce(failed("decision_reason_required"));
    await f.controller.confirm("", false);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", operationId: null });
    await f.controller.confirm("Corrected explanation", false);
    expect(f.actions.decideSubmission.execute.mock.calls[1]?.[1]).toBe(expenseIdentifier(7002));
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });

  it("selects only a reviewed item without refetching and ignores revocation from an obsolete source", async () => {
    const f = fixture();
    await activate(f);
    const line = f.review.lines[0];
    assert(line);
    f.controller.selectLine(line.id);
    expect(f.controller.getSnapshot().selectedLineId).toBe(line.id);
    f.controller.selectLine(expenseIdentifier(999));
    expect(f.controller.getSnapshot().selectedLineId).toBe(line.id);
    f.controller.selectLine(null);
    expect(f.controller.getSnapshot().selectedLineId).toBeNull();
    expect(f.actions.reviewDecision.execute).toHaveBeenCalledTimes(1);
    const failure = { code: "expense_receipt_not_found", fields: {}, parameters: {} };
    f.controller.revokeEvidence(submission(), failure);
    expect(f.controller.getSnapshot().review).toBe(f.review);
    f.controller.revokeEvidence(f.review, failure);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure,
    });
    const replacement = submission();
    f.actions.reviewDecision.execute.mockResolvedValueOnce(success(replacement));
    await f.controller.refresh();
    f.controller.revokeEvidence(f.review, failure);
    expect(f.controller.getSnapshot().review).toBe(replacement);
  });

  it("hides revoked evidence without guessing or cancelling an in-flight decision", async () => {
    const f = fixture(),
      pending = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.decideSubmission.execute.mockReturnValueOnce(pending.promise);
    const saving = f.controller.confirm("Original evidence", false);
    const signal = f.actions.decideSubmission.execute.mock.calls[0]?.[6];
    assert(signal);
    f.controller.revokeEvidence(f.review, { code: "access_denied", fields: {}, parameters: {} });
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "submitting",
      review: null,
      operationId: expenseIdentifier(7001),
    });
    expect(signal.aborted).toBe(false);
    pending.resolve(failed("connection_unavailable"));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.nextIdentifier).toHaveBeenCalledTimes(1);
  });

  it("aborts an abandoned write and ignores its late completion after reactivation", async () => {
    const f = fixture(),
      pending = deferred<typeof f.receipt>();
    await activate(f);
    f.actions.decideSubmission.execute.mockReturnValueOnce(pending.promise);
    const saving = f.controller.confirm("Abandoned command", false);
    const signal = f.actions.decideSubmission.execute.mock.calls[0]?.[6];
    const listener = vi.fn(),
      unsubscribe = f.controller.subscribe(listener);
    unsubscribe();
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    await activate(f);
    pending.resolve(success(f.receipt));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      receipt: null,
      operationId: null,
    });
    expect(listener).not.toHaveBeenCalled();
  });

  it("does not let a disposed read restore evidence", async () => {
    const f = fixture(),
      pending = deferred<typeof f.review>();
    f.actions.reviewDecision.execute.mockReturnValueOnce(pending.promise);
    f.controller.activate();
    const signal = f.actions.reviewDecision.execute.mock.calls[0]?.[2];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    pending.resolve(success(f.review));
    await Promise.resolve();
    expect(f.controller.getSnapshot().review).toBeNull();
    await f.controller.confirm("Cannot submit", false);
    expect(f.actions.decideSubmission.execute).not.toHaveBeenCalled();
  });

  it("sanitizes unexpected failures while preserving unknown write outcomes for retry", async () => {
    const f = fixture();
    f.actions.reviewDecision.execute.mockRejectedValueOnce(new Error("PRIVATE READ"));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.refresh();
    f.actions.decideSubmission.execute.mockRejectedValueOnce(new Error("PRIVATE WRITE"));
    await f.controller.confirm("Reviewed", false);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });
});
