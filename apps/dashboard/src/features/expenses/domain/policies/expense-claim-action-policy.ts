import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimId } from "../entities/expense-claim";
import { type ExpenseClaimAction, expenseClaimActions } from "../entities/expense-claim-action";
import type { ExpenseClaimActionCommand } from "../entities/expense-claim-action-command";
import type { ExpenseClaimActionSnapshot } from "../entities/expense-claim-action-snapshot";
import { canManageExpenseDrafts, expenseDraftAccessLost } from "./expense-draft-policy";
export function availableExpenseClaimActions(
  access: CompanyAccess,
  claim: ExpenseClaimActionSnapshot,
): readonly ExpenseClaimAction[] {
  if (claim.companyId !== access.companyId || !canManageExpenseDrafts(access.permissions))
    return [];
  if (claim.status === "DRAFT")
    return claim.lineCount > 0 && claim.submissionCount < 20 ? ["SUBMIT", "CANCEL"] : ["CANCEL"];
  if (claim.status === "RETURNED") return ["CANCEL"];
  if (claim.status === "PENDING" && claim.latestSubmissionId !== null) return ["WITHDRAW"];
  return [];
}
export function prepareExpenseClaimAction(
  access: CompanyAccess,
  operation: OperationId,
  claim: ExpenseClaimActionSnapshot,
  action: ExpenseClaimAction,
  inputReason: string,
  submissionId: string | null,
): Result<ExpenseClaimActionCommand> {
  if (claim.companyId !== access.companyId || !canManageExpenseDrafts(access.permissions))
    return failed("access_denied");
  const reason = inputReason.trim();
  if (
    !expenseClaimActions.includes(action) ||
    !isUuid(operation) ||
    !isUuid(claim.id) ||
    !Number.isSafeInteger(claim.version) ||
    claim.version < 0 ||
    claim.version >= Number.MAX_SAFE_INTEGER ||
    reason.length < 1 ||
    reason.length > 1000
  )
    return failed("invalid_expense_action");
  if (!availableExpenseClaimActions(access, claim).includes(action))
    return failed("expense_claim_action_unavailable");
  const fields = {
    claimId: claim.id.toLowerCase() as ExpenseClaimId,
    version: claim.version,
    reason,
  };
  if (action === "CANCEL") return success(Object.freeze({ ...fields, action }));
  const target = action === "SUBMIT" ? submissionId : claim.latestSubmissionId;
  if (!target || !isUuid(target)) return failed("invalid_expense_action");
  return success(Object.freeze({ ...fields, action, submissionId: target.toLowerCase() }));
}
export const expenseClaimReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "expense_not_editable",
    "expense_submission_not_current",
    "expense_submission_limit",
    "expense_claim_action_unavailable",
    "approval_changed",
    "approval_unavailable",
  ].includes(failure.code);
export const expenseClaimActionWasRejected = (failure: Failure): boolean =>
  expenseDraftAccessLost(failure) ||
  expenseClaimReviewChanged(failure) ||
  [
    "invalid_expense_action",
    "expense_lines_required",
    "expense_transaction_date_invalid",
    "expense_employment_ineligible",
    "expense_transaction_expired",
    "expense_line_limit_exceeded",
    "expense_cost_center_required",
    "expense_receipt_required",
    "expense_category_limit_exceeded",
    "expense_category_unavailable",
    "expense_cost_center_unavailable",
    "expense_receipt_not_ready",
    "expense_receipt_unavailable",
    "expense_approval_requires_split",
    "approval_group_too_large",
    "expense_draft_revision_limit",
    "approval_policy_capacity",
    "approval_policy_missing",
    "approval_exclusion_capacity",
    "approval_policy_empty",
    "ambiguous_approval_policy",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
