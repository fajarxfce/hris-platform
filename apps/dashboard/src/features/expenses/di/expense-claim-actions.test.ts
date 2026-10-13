import { assert, describe, expect, it, vi } from "vitest";
import { expenseClaimRecord, expenseIdentifier } from "../../../../tests/fixtures/expense-claims";
import {
  expenseDraftRecord,
  expenseDraftReferences,
} from "../../../../tests/fixtures/expense-drafts";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toExpenseClaimReview } from "../data/mappers/expense-claim-review-mapper";
import type { ExpenseClaimId } from "../domain/entities/expense-claim";
import {
  type ExpenseClaimAction,
  expenseClaimActions,
} from "../domain/entities/expense-claim-action";
import { expenseClaimActionSnapshot } from "../domain/entities/expense-claim-action-snapshot";
import {
  availableExpenseClaimActions,
  expenseClaimActionWasRejected,
} from "../domain/policies/expense-claim-action-policy";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId,
  access = { companyId: company, permissions: ["expenses.manage"] };
const operation = expenseIdentifier(900) as OperationId,
  newSubmission = expenseIdentifier(901);
const signal = () => new AbortController().signal;
function record(action: ExpenseClaimAction) {
  return action === "WITHDRAW"
    ? expenseClaimRecord(0, 1, 1)
    : expenseDraftRecord(0, action === "CANCEL");
}
function snapshot(action: ExpenseClaimAction) {
  const raw = record(action);
  return expenseClaimActionSnapshot(
    toExpenseClaimReview(raw, company, raw.claim.id as ExpenseClaimId, null).claim,
  );
}
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createExpenseFeature({ request }, { downloadBinary: vi.fn(), downloadText: vi.fn() }),
  };
}
describe("expense claim action boundaries", () => {
  it.each(expenseClaimActions)("reviews %s with only the required metadata", async (action) => {
    const f = fixture(),
      raw = record(action);
    f.request.mockResolvedValueOnce(raw).mockResolvedValueOnce(expenseDraftReferences());
    const result = await f.feature.reviewClaimAction.execute(
      access,
      raw.claim.id,
      action,
      signal(),
    );
    assert(result.ok);
    expect(result.value.claim.id).toBe(raw.claim.id);
    expect(result.value.references === null).toBe(action !== "SUBMIT");
    expect(f.request).toHaveBeenCalledTimes(action === "SUBMIT" ? 2 : 1);
    expect(f.request.mock.calls[0]?.[0].path).toContain(
      `/claims/${raw.claim.id}/review?historyLimit=20&submissionsLimit=20`,
    );
  });
  it.each(expenseClaimActions)(
    "sends %s at the reviewed version and validates the claim receipt",
    async (action) => {
      const f = fixture(),
        reviewed = snapshot(action);
      f.request.mockResolvedValue({ id: reviewed.id, version: reviewed.version + 1 });
      expect(
        await f.feature.transitionClaim.execute(
          access,
          operation,
          reviewed,
          action,
          "  Reviewed action  ",
          newSubmission,
          signal(),
        ),
      ).toEqual({ ok: true, value: { id: reviewed.id, version: reviewed.version + 1 } });
      expect(f.request.mock.calls[0]?.[0]).toEqual({
        path: `/api/v1/companies/${company}/expenses/${action === "WITHDRAW" ? `submissions/${reviewed.latestSubmissionId}/withdraw` : `claims/${reviewed.id}/${action.toLowerCase()}`}`,
        method: "POST",
        operationId: operation,
        body: {
          expectedVersion: reviewed.version,
          reason: "Reviewed action",
          ...(action === "SUBMIT" ? { submissionId: newSubmission } : {}),
        },
      });
    },
  );
  it("keeps cancellation available at draft/submission limits and submission after the final editable revision", () => {
    const current = snapshot("SUBMIT");
    expect(
      availableExpenseClaimActions(access, { ...current, lineCount: 0, submissionCount: 20 }),
    ).toEqual(["CANCEL"]);
    expect(
      availableExpenseClaimActions(access, { ...current, status: "RETURNED", submissionCount: 20 }),
    ).toEqual(["CANCEL"]);
    const raw = expenseDraftRecord();
    raw.claim.version = 99;
    raw.claim.draftRevision = 99;
    raw.claim.draft.revision = 99;
    expect(
      availableExpenseClaimActions(
        access,
        expenseClaimActionSnapshot({
          ...toExpenseClaimReview(
            expenseDraftRecord(),
            company,
            raw.claim.id as ExpenseClaimId,
            null,
          ).claim,
          version: 99,
          draftRevision: 99,
        }),
      ),
    ).toEqual(["SUBMIT", "CANCEL"]);
    expect(availableExpenseClaimActions(access, snapshot("WITHDRAW"))).toEqual(["WITHDRAW"]);
  });
  it.each(["APPROVED", "REJECTED", "CANCELLED"] as const)(
    "exposes no write action for a %s claim",
    (status) => {
      expect(availableExpenseClaimActions(access, { ...snapshot("SUBMIT"), status })).toEqual([]);
    },
  );
  it("denies read-only or self scope, invalid IDs, foreign snapshots and unavailable transitions before writes", async () => {
    const f = fixture(),
      current = snapshot("SUBMIT");
    for (const permissions of [["expenses.read"], ["expenses.self.manage"], ["expenses.approve"]]) {
      expect(
        (
          await f.feature.reviewClaimAction.execute(
            { ...access, permissions },
            current.id,
            "SUBMIT",
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(
        (
          await f.feature.transitionClaim.execute(
            { ...access, permissions },
            operation,
            current,
            "SUBMIT",
            "Review",
            newSubmission,
            signal(),
          )
        ).ok,
      ).toBe(false);
    }
    expect((await f.feature.reviewClaimAction.execute(access, "../", "CANCEL", signal())).ok).toBe(
      false,
    );
    expect(
      (
        await f.feature.transitionClaim.execute(
          access,
          operation,
          { ...current, companyId: expenseIdentifier(3) as CompanyId },
          "SUBMIT",
          "Review",
          newSubmission,
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await f.feature.transitionClaim.execute(
          access,
          operation,
          current,
          "WITHDRAW",
          "Review",
          newSubmission,
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each(["note", "version", "new-id", "operation"])(
    "rejects invalid %s without sending",
    async (problem) => {
      const f = fixture(),
        current = snapshot("SUBMIT");
      const result = await f.feature.transitionClaim.execute(
        access,
        problem === "operation" ? ("wrong" as OperationId) : operation,
        problem === "version" ? { ...current, version: Number.MAX_SAFE_INTEGER } : current,
        "SUBMIT",
        problem === "note" ? " " : "Reviewed",
        problem === "new-id" ? null : newSubmission,
        signal(),
      );
      expect(result).toMatchObject({ ok: false, failure: { code: "invalid_expense_action" } });
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it("does not acquire submission references for an empty draft or returned claim", async () => {
    const f = fixture(),
      raw = expenseDraftRecord();
    raw.claim.draft.lines = [];
    raw.claim.draft.totalAmount = "0.00";
    f.request.mockResolvedValueOnce(raw).mockResolvedValueOnce(expenseDraftRecord(0, true));
    expect(
      await f.feature.reviewClaimAction.execute(access, raw.claim.id, "SUBMIT", signal()),
    ).toMatchObject({ ok: false, failure: { code: "expense_claim_action_unavailable" } });
    expect(
      await f.feature.reviewClaimAction.execute(access, raw.claim.id, "SUBMIT", signal()),
    ).toMatchObject({ ok: false, failure: { code: "expense_claim_action_unavailable" } });
    expect(f.request).toHaveBeenCalledTimes(2);
  });
  it.each([
    "approval_policy_missing",
    "expense_receipt_not_ready",
    "expense_transaction_expired",
    "expense_approval_requires_split",
    "approval_changed",
    "mfa_required",
  ])("preserves the definite %s failure without raw technical messages", async (code) => {
    const f = fixture();
    f.request.mockRejectedValue(
      new HttpResponseError(409, { code, detail: "PRIVATE EXPENSE DATA" }),
    );
    expect(
      await f.feature.transitionClaim.execute(
        access,
        operation,
        snapshot("SUBMIT"),
        "SUBMIT",
        "Reviewed",
        newSubmission,
        signal(),
      ),
    ).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    expect(expenseClaimActionWasRejected({ code, fields: {}, parameters: {} })).toBe(true);
  });
  it.each(expenseClaimActions)(
    "does not accept an unrelated or wrong-version receipt for %s",
    async (action) => {
      const f = fixture(),
        current = snapshot(action);
      f.request
        .mockResolvedValueOnce({ id: newSubmission, version: current.version + 1 })
        .mockResolvedValueOnce({ id: current.id, version: current.version });
      for (let index = 0; index < 2; index++)
        expect(
          await f.feature.transitionClaim.execute(
            access,
            operation,
            current,
            action,
            "Reviewed",
            newSubmission,
            signal(),
          ),
        ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
  it("propagates cancellation during metadata review and after a late committed response", async () => {
    const f = fixture(),
      reviewing = new AbortController();
    f.request.mockImplementationOnce(async () => {
      reviewing.abort();
      return record("SUBMIT");
    });
    await expect(
      f.feature.reviewClaimAction.execute(access, expenseIdentifier(1), "SUBMIT", reviewing.signal),
    ).rejects.toThrow();
    expect(f.request).toHaveBeenCalledTimes(1);
    const writing = new AbortController();
    f.request.mockImplementationOnce(async () => {
      writing.abort();
      return { id: expenseIdentifier(1), version: 1 };
    });
    await expect(
      f.feature.transitionClaim.execute(
        access,
        operation,
        snapshot("SUBMIT"),
        "SUBMIT",
        "Reviewed",
        newSubmission,
        writing.signal,
      ),
    ).rejects.toThrow();
  });
});
