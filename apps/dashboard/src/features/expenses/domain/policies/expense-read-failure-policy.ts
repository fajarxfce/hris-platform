import type { Failure } from "../../../../core/domain/result";
export const expenseEvidenceUnavailable = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "expense_receipt_not_found",
    "expense_submission_not_found",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
  ].includes(failure.code);
