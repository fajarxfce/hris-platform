import { afterEach, assert, describe, expect, it, vi } from "vitest";
import {
  expenseClaimRecord,
  expenseIdentifier,
} from "../../../../../tests/fixtures/expense-claims";
import {
  expenseDraftRecord,
  expenseDraftReferences,
} from "../../../../../tests/fixtures/expense-drafts";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toExpenseClaimReview } from "../../data/mappers/expense-claim-review-mapper";
import { toExpenseDraftReferences } from "../../data/mappers/expense-drafting-mapper";
import type { ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseClaimAction } from "../../domain/entities/expense-claim-action";
import type { ExpenseClaimActionReview } from "../../domain/entities/expense-claim-action-review";
import { expenseDraftReferenceQuery } from "../../domain/policies/expense-draft-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpenseClaimActionController } from "./expense-claim-action-controller";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  access = { companyId: company, permissions: ["expenses.manage"] },
  id = expenseIdentifier(1);
const controllers: ExpenseClaimActionController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
function deferred<T>() {
  let resolve!: (result: Result<T>) => void;
  const promise = new Promise<Result<T>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
async function fixture(action: ExpenseClaimAction = "SUBMIT") {
  const raw = action === "WITHDRAW" ? expenseClaimRecord(0, 1, 1) : expenseDraftRecord(),
    claim = toExpenseClaimReview(raw, company, id as ExpenseClaimId, null).claim;
  const review = {
    claim,
    references:
      action === "SUBMIT"
        ? toExpenseDraftReferences(
            expenseDraftReferences(),
            company,
            claim.employmentId,
            expenseDraftReferenceQuery(claim.draft.lines),
          )
        : null,
  };
  const actions = {
    reviewClaimAction: {
      execute: vi
        .fn<ExpenseUseCases["reviewClaimAction"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    transitionClaim: {
      execute: vi
        .fn<ExpenseUseCases["transitionClaim"]["execute"]>()
        .mockResolvedValue(success({ id, version: claim.version + 1 })),
    },
  };
  let next = 1000;
  const nextId = vi.fn(() => expenseIdentifier(++next));
  const controller = new ExpenseClaimActionController(actions, access, id, action, nextId);
  controllers.push(controller);
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));
  return { controller, actions, review, nextId };
}
describe("expense claim action lifecycle", () => {
  it.each(["SUBMIT", "CANCEL", "WITHDRAW"] as const)(
    "captures one %s command and refuses competing confirmations while pending",
    async (action) => {
      const f = await fixture(action),
        pending = deferred<{ id: string; version: number }>();
      f.actions.transitionClaim.execute.mockReturnValueOnce(pending.promise);
      const saving = f.controller.confirm("Reviewed"),
        current = f.actions.transitionClaim.execute.mock.calls[0];
      assert(current);
      await f.controller.confirm("Different input");
      await f.controller.retry();
      await f.controller.refresh();
      expect(f.actions.transitionClaim.execute).toHaveBeenCalledTimes(1);
      expect(f.actions.reviewClaimAction.execute).toHaveBeenCalledTimes(1);
      expect(current[2]).toMatchObject({
        id,
        version: f.review.claim.version,
        latestSubmissionId: f.review.claim.latestSubmissionId,
      });
      expect(current[5] === null).toBe(action !== "SUBMIT");
      expect(f.nextId).toHaveBeenCalledTimes(action === "SUBMIT" ? 2 : 1);
      pending.resolve(success({ id, version: f.review.claim.version + 1 }));
      await saving;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        review: null,
        receipt: { id, version: f.review.claim.version + 1 },
      });
    },
  );
  it.each(["SUBMIT", "CANCEL", "WITHDRAW"] as const)(
    "retains original %s input through response loss, MFA and a later conflict",
    async (action) => {
      const f = await fixture(action);
      f.actions.transitionClaim.execute
        .mockResolvedValueOnce(failed("connection_unavailable"))
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockResolvedValueOnce(failed("stale_version"))
        .mockResolvedValueOnce(success({ id, version: f.review.claim.version + 1 }));
      await f.controller.confirm("Original reason");
      const original = f.actions.transitionClaim.execute.mock.calls[0];
      assert(original);
      expect(Object.isFrozen(original[2])).toBe(true);
      await f.controller.retry();
      expect(f.controller.getSnapshot().review).toBeNull();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      await f.controller.confirm("New reason");
      await f.controller.refresh();
      expect(f.actions.transitionClaim.execute).toHaveBeenCalledTimes(2);
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      await f.controller.retry();
      for (const call of f.actions.transitionClaim.execute.mock.calls)
        expect(call.slice(0, 6)).toEqual(original.slice(0, 6));
      expect(f.controller.getSnapshot().stage).toBe("saved");
      expect(f.nextId).toHaveBeenCalledTimes(action === "SUBMIT" ? 2 : 1);
    },
  );
  it("requires explicit reload after a competing decision without changing its observed version", async () => {
    const f = await fixture("WITHDRAW");
    f.actions.transitionClaim.execute.mockResolvedValueOnce(failed("approval_changed"));
    await f.controller.confirm("Withdraw");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.confirm("Try again");
    await f.controller.retry();
    expect(f.actions.transitionClaim.execute).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot().review?.claim.version).toBe(1);
    f.actions.reviewClaimAction.execute.mockResolvedValue(
      failed("expense_claim_action_unavailable"),
    );
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
  });
  it("keeps a definite eligibility failure reviewable and starts a new command only after confirmation", async () => {
    const f = await fixture();
    f.actions.transitionClaim.execute.mockResolvedValueOnce(failed("expense_receipt_not_ready"));
    await f.controller.confirm("Receipt pending");
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    expect(f.controller.getSnapshot().operationId).toBeNull();
    await f.controller.retry();
    expect(f.actions.transitionClaim.execute).toHaveBeenCalledTimes(1);
    await f.controller.confirm("Receipt ready");
    expect(f.actions.transitionClaim.execute).toHaveBeenCalledTimes(2);
    expect(f.actions.transitionClaim.execute.mock.calls[0]?.[1]).not.toBe(
      f.actions.transitionClaim.execute.mock.calls[1]?.[1],
    );
    expect(f.actions.transitionClaim.execute.mock.calls[0]?.[5]).not.toBe(
      f.actions.transitionClaim.execute.mock.calls[1]?.[5],
    );
  });
  it("hides claim and receipt metadata after definite access loss", async () => {
    const f = await fixture();
    f.controller.selectLine(f.review.claim.draft.lines[0]?.id ?? null);
    f.actions.transitionClaim.execute.mockResolvedValue(failed("expense_access_denied"));
    await f.controller.confirm("Reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      selectedLineId: null,
      operationId: null,
    });
  });
  it("cancels obsolete reviews and ignores late rejection after replacement", async () => {
    const f = await fixture(),
      pending = deferred<ExpenseClaimActionReview>();
    f.actions.reviewClaimAction.execute.mockReturnValueOnce(pending.promise);
    const previous = f.controller.refresh(),
      signal = f.actions.reviewClaimAction.execute.mock.calls.at(-1)?.[3];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    pending.resolve(failed("access_denied"));
    await previous;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", failure: null });
  });
  it.each(["success", "failure"])(
    "disposes a pending command and rejects its late %s",
    async (outcome) => {
      const f = await fixture(),
        pending = deferred<{ id: string; version: number }>();
      f.actions.transitionClaim.execute.mockReturnValueOnce(pending.promise);
      const saving = f.controller.confirm("Reviewed"),
        signal = f.actions.transitionClaim.execute.mock.calls[0]?.[6];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      pending.resolve(
        outcome === "success" ? success({ id, version: 1 }) : failed("access_denied"),
      );
      await saving;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        review: null,
        receipt: null,
        operationId: null,
      });
    },
  );
});
