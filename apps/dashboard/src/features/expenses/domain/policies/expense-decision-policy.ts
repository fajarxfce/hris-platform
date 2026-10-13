import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type ExpenseDecision, expenseDecisions } from "../entities/expense-decision";
import type { ExpenseDecisionCommand } from "../entities/expense-decision-command";
import type { ExpenseDecisionSnapshot } from "../entities/expense-decision-snapshot";
import { expenseEvidenceUnavailable } from "./expense-read-failure-policy";

export const canReviewExpenses = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["expenses.approve", "expenses.team.approve"].includes(permission),
  );

export const canDecideExpenseSubmission = (
  access: CompanyAccess,
  review: ExpenseDecisionSnapshot,
): boolean =>
  access.companyId === review.companyId &&
  canReviewExpenses(access.permissions) &&
  review.availableActions.includes("DECIDE") &&
  review.current &&
  review.claimStatus === "PENDING" &&
  review.approvalStatus === "PENDING";

export const expenseDecisionNeedsReason = (
  decision: ExpenseDecision,
  duplicateCount: number,
): boolean => decision !== "APPROVE" || duplicateCount > 0;

export const expenseDecisionNeedsAcknowledgement = (
  decision: ExpenseDecision,
  duplicateCount: number,
): boolean => decision === "APPROVE" && duplicateCount > 0;

export function prepareExpenseDecision(
  access: CompanyAccess,
  operation: OperationId,
  review: ExpenseDecisionSnapshot,
  decision: ExpenseDecision,
  inputReason: string,
  acknowledgeDuplicates: boolean,
): Result<ExpenseDecisionCommand> {
  if (access.companyId !== review.companyId || !canReviewExpenses(access.permissions))
    return failed("access_denied");
  if (!canDecideExpenseSubmission(access, review)) return failed("expense_decision_unavailable");
  const reason = inputReason.trim();
  if (
    !expenseDecisions.includes(decision) ||
    !isUuid(operation) ||
    !isUuid(review.id) ||
    !isUuid(review.claimId) ||
    !Number.isSafeInteger(review.claimVersion) ||
    review.claimVersion < 0 ||
    review.claimVersion >= Number.MAX_SAFE_INTEGER ||
    !Number.isSafeInteger(review.approvalVersion) ||
    review.approvalVersion < 0 ||
    review.approvalVersion >= Number.MAX_SAFE_INTEGER ||
    reason.length > 1000 ||
    typeof acknowledgeDuplicates !== "boolean"
  )
    return failed("invalid_expense_decision");
  if (
    expenseDecisionNeedsAcknowledgement(decision, review.duplicateReceiptDigests.length) &&
    (!acknowledgeDuplicates || reason.length === 0)
  )
    return failed("expense_duplicate_acknowledgement_required");
  if (
    expenseDecisionNeedsReason(decision, review.duplicateReceiptDigests.length) &&
    reason.length === 0
  )
    return failed("decision_reason_required");
  return success(
    Object.freeze({
      id: review.id,
      claimId: review.claimId,
      claimVersion: review.claimVersion,
      approvalVersion: review.approvalVersion,
      decision,
      reason,
      acknowledgeDuplicates,
    }),
  );
}

export const expenseDecisionReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "approval_changed",
    "approval_unavailable",
    "approval_not_pending",
    "expense_decision_unavailable",
    "expense_submission_not_current",
    "expense_claim_not_found",
    "expense_submission_not_found",
    "self_approval_denied",
    "not_assigned_approver",
    "expense_duplicate_acknowledgement_required",
  ].includes(failure.code);

export const expenseDecisionWasRejected = (failure: Failure): boolean =>
  expenseEvidenceUnavailable(failure) ||
  expenseDecisionReviewChanged(failure) ||
  [
    "invalid_expense_decision",
    "decision_reason_required",
    "approval_delegation_capacity",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
