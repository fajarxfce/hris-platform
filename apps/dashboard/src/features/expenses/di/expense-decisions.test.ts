import { assert, describe, expect, it, vi } from "vitest";
import {
  expenseIdentifier,
  expenseSubmissionRecord,
} from "../../../../tests/fixtures/expense-claims";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { success } from "../../../core/domain/result";
import { toExpenseSubmission } from "../data/mappers/expense-submission-mapper";
import type { ExpenseSubmissionDto } from "../data/models/expense-submission-dto";
import type { ExpenseDecision } from "../domain/entities/expense-decision";
import { expenseDecisionSnapshot } from "../domain/entities/expense-decision-snapshot";
import type { ExpenseSubmissionId } from "../domain/entities/expense-submission-summary";
import { createExpenseFeature } from "./expense-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId: company, permissions: ["expenses.approve"] };
const operation = expenseIdentifier(999) as OperationId;
const signal = () => new AbortController().signal;
const source = (raw: ExpenseSubmissionDto = expenseSubmissionRecord()) =>
  toExpenseSubmission(raw, company, raw.id as ExpenseSubmissionId);
function fixture() {
  const request = vi.fn();
  return {
    request,
    feature: createExpenseFeature(
      { request },
      {
        downloadBinary: vi.fn().mockResolvedValue(success(undefined)),
      },
    ),
  };
}
describe("expense decision boundary", () => {
  it("requires reviewer permission and a valid submission before acquiring evidence", async () => {
    const f = fixture(),
      raw = expenseSubmissionRecord();
    for (const permissions of [
      [],
      ["expenses.read"],
      ["expenses.manage"],
      ["approvals.read"],
      ["expenses.pay"],
    ])
      expect(
        await f.feature.reviewDecision.execute(
          { companyId: company, permissions },
          raw.id,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await f.feature.reviewDecision.execute(access, "../private", signal())).toMatchObject({
      ok: false,
      failure: { code: "expense_submission_not_found" },
    });
    expect(f.request).not.toHaveBeenCalled();
    f.request.mockResolvedValue(raw);
    for (const permission of ["expenses.approve", "expenses.team.approve"])
      expect(
        await f.feature.reviewDecision.execute(
          { companyId: company, permissions: [permission] },
          raw.id,
          signal(),
        ),
      ).toMatchObject({ ok: true, value: { availableActions: ["DECIDE"] } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${company}/expenses/submissions/${raw.id}`,
    });
  });

  it("keeps readable submitted evidence separate from the server's decision availability", async () => {
    const f = fixture(),
      raw = expenseSubmissionRecord();
    raw.availableActions = [];
    raw.canReadClaim = false;
    f.request.mockResolvedValue(raw);
    expect(await f.feature.loadSubmission.execute(access, raw.id, signal())).toMatchObject({
      ok: true,
      value: { canReadClaim: false, availableActions: [] },
    });
    expect(await f.feature.reviewDecision.execute(access, raw.id, signal())).toMatchObject({
      ok: false,
      failure: { code: "expense_decision_unavailable" },
    });
  });

  it.each(["historical", "returned", "blocked"])(
    "rejects inconsistent %s decision metadata",
    async (kind) => {
      const f = fixture(),
        raw = expenseSubmissionRecord();
      if (kind === "historical") raw.current = false;
      if (kind === "returned") raw.claimStatus = "RETURNED";
      if (kind === "blocked") raw.approval.status = "BLOCKED";
      f.request.mockResolvedValue(raw);
      expect(await f.feature.loadSubmission.execute(access, raw.id, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );

  it.each(["APPROVE", "REJECT", "RETURN"] as const)(
    "sends %s with both observed versions and a stable operation",
    async (decision) => {
      const f = fixture(),
        review = source(),
        facts = expenseDecisionSnapshot(review);
      f.request.mockResolvedValue({
        id: review.claimId,
        version: review.claimVersion + 1,
        replayed: true,
      });
      const result = await f.feature.decideSubmission.execute(
        access,
        operation,
        facts,
        decision,
        "  Reviewed evidence  ",
        false,
        signal(),
      );
      expect(result).toEqual(success({ id: review.claimId, version: review.claimVersion + 1 }));
      expect(f.request.mock.calls[0]?.[0]).toEqual({
        path: `/api/v1/companies/${company}/expenses/submissions/${review.id}/decisions`,
        method: "POST",
        operationId: operation,
        body: {
          expectedVersion: review.claimVersion,
          expectedApprovalVersion: review.approval.version,
          decision,
          reason: "Reviewed evidence",
          acknowledgeDuplicates: false,
        },
      });
    },
  );

  it("rejects another company, unavailable stages and invalid versions before HTTP", async () => {
    const f = fixture(),
      facts = expenseDecisionSnapshot(source());
    expect(
      await f.feature.decideSubmission.execute(
        { ...access, companyId: expenseIdentifier(2) as CompanyId },
        operation,
        facts,
        "APPROVE",
        "",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const changed of [
      { availableActions: [] },
      { current: false },
      { claimStatus: "DRAFT" as const },
      { approvalStatus: "BLOCKED" as const },
    ])
      expect(
        await f.feature.decideSubmission.execute(
          access,
          operation,
          { ...facts, ...changed },
          "APPROVE",
          "",
          false,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "expense_decision_unavailable" } });
    for (const changed of [
      { claimVersion: -1 },
      { claimVersion: Number.NaN },
      { claimVersion: Number.MAX_SAFE_INTEGER },
      { approvalVersion: 0.5 },
      { approvalVersion: -1 },
    ])
      expect(
        await f.feature.decideSubmission.execute(
          access,
          operation,
          { ...facts, ...changed },
          "APPROVE",
          "",
          false,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_expense_decision" } });
    expect(
      await f.feature.decideSubmission.execute(
        access,
        "bad" as OperationId,
        facts,
        "APPROVE",
        "",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await f.feature.decideSubmission.execute(
        access,
        operation,
        facts,
        "CANCEL" as ExpenseDecision,
        "",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("requires acknowledgement and an explanation only when approving current duplicate signals", async () => {
    const f = fixture(),
      raw = expenseSubmissionRecord();
    const receipt = raw.lines[0]?.receipts[0];
    assert(receipt);
    raw.duplicateReceiptDigests = [receipt.sha256];
    const review = source(raw),
      facts = expenseDecisionSnapshot(review);
    for (const [acknowledge, reason] of [
      [false, ""],
      [true, "  "],
      [false, "Checked duplicate"],
    ] as const)
      expect(
        await f.feature.decideSubmission.execute(
          access,
          operation,
          facts,
          "APPROVE",
          reason,
          acknowledge,
          signal(),
        ),
      ).toMatchObject({
        ok: false,
        failure: { code: "expense_duplicate_acknowledgement_required" },
      });
    for (const decision of ["REJECT", "RETURN"] as const)
      expect(
        await f.feature.decideSubmission.execute(
          access,
          operation,
          facts,
          decision,
          " ",
          false,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "decision_reason_required" } });
    expect(
      await f.feature.decideSubmission.execute(
        access,
        operation,
        facts,
        "APPROVE",
        "x".repeat(1001),
        true,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_expense_decision" } });
    expect(f.request).not.toHaveBeenCalled();
    f.request.mockResolvedValue({ id: review.claimId, version: review.claimVersion + 1 });
    expect(
      await f.feature.decideSubmission.execute(
        access,
        operation,
        facts,
        "APPROVE",
        "Distinct reimbursable portion",
        true,
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(
      await f.feature.decideSubmission.execute(
        access,
        operation,
        facts,
        "RETURN",
        "Provide corrected evidence",
        false,
        signal(),
      ),
    ).toMatchObject({ ok: true });
  });

  it("does not treat a receipt for another resource or version as a confirmed decision", async () => {
    const f = fixture(),
      review = source(),
      facts = expenseDecisionSnapshot(review);
    for (const receipt of [
      { id: review.id, version: review.claimVersion + 1 },
      { id: review.claimId, version: review.claimVersion },
      { id: review.claimId, version: -1 },
    ]) {
      f.request.mockResolvedValue(receipt);
      expect(
        await f.feature.decideSubmission.execute(
          access,
          operation,
          facts,
          "APPROVE",
          "",
          false,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    f.request.mockRejectedValue(
      new HttpResponseError(409, { code: "approval_changed", detail: "PRIVATE TECHNICAL TEXT" }),
    );
    const rejected = await f.feature.decideSubmission.execute(
      access,
      operation,
      facts,
      "APPROVE",
      "",
      false,
      signal(),
    );
    expect(rejected).toMatchObject({ ok: false, failure: { code: "approval_changed" } });
    expect(JSON.stringify(rejected)).not.toContain("PRIVATE");
  });

  it("keeps reviewed flags immutable and propagates cancellation after acquisition", async () => {
    const raw = expenseSubmissionRecord(),
      review = source(raw),
      facts = expenseDecisionSnapshot(review);
    raw.availableActions.length = 0;
    expect(facts.availableActions).toEqual(["DECIDE"]);
    expect(Object.isFrozen(facts.duplicateReceiptDigests)).toBe(true);
    const f = fixture(),
      abort = new AbortController();
    f.request.mockImplementation(async () => {
      abort.abort();
      return { id: review.claimId, version: review.claimVersion + 1 };
    });
    await expect(
      f.feature.decideSubmission.execute(
        access,
        operation,
        facts,
        "APPROVE",
        "",
        false,
        abort.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
