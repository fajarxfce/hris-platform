import type { Failure } from "../../../../core/domain/result";
export const payrollPaymentEvidenceUnavailable = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "payroll_payment_batch_not_found",
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
