import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type {
  EmployeeImportApplication,
  EmployeeImportChange,
} from "../entities/employee-import-change";

export function normalizeEmployeeImportChange(
  input: EmployeeImportChange,
): Result<EmployeeImportChange> {
  const change = Object.freeze({
    importId: input.importId.toLowerCase(),
    expectedVersion: input.expectedVersion,
    reason: input.reason.trim(),
  });
  if (
    !isUuid(change.importId) ||
    !Number.isSafeInteger(change.expectedVersion) ||
    change.expectedVersion < 0 ||
    change.expectedVersion === Number.MAX_SAFE_INTEGER ||
    change.reason.length < 1 ||
    change.reason.length > 1000
  )
    return failed("invalid_employee_import");
  return success(change);
}
export function normalizeEmployeeImportApplication(
  input: EmployeeImportApplication,
): Result<EmployeeImportApplication> {
  const change = normalizeEmployeeImportChange(input);
  if (!change.ok) return change;
  if (typeof input.allowPartial !== "boolean") return failed("invalid_employee_import");
  return success(Object.freeze({ ...change.value, allowPartial: input.allowPartial }));
}
export const employeeImportChangeWasRejected = (failure: Failure): boolean =>
  [
    "invalid_employee_import",
    "employee_import_access_required",
    "employee_import_not_found",
    "employee_import_not_ready",
    "employee_import_has_no_ready_rows",
    "employee_import_has_invalid_rows",
    "employee_import_job_not_stopped",
    "employee_import_is_terminal",
    "job_queue_full",
    "stale_version",
    "stale_job_version",
    "data_conflict",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
export const employeeImportReviewChanged = (failure: Failure): boolean =>
  [
    "employee_import_not_found",
    "employee_import_not_ready",
    "employee_import_has_no_ready_rows",
    "employee_import_has_invalid_rows",
    "employee_import_job_not_stopped",
    "employee_import_is_terminal",
    "stale_version",
    "stale_job_version",
    "data_conflict",
  ].includes(failure.code);
