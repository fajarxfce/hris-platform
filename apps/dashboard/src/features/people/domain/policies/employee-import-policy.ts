import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";

export const canImportEmployees = (permissions: readonly string[]): boolean =>
  ["people.import", "people.manage", "people.profile.read", "people.profile.manage"].every(
    (permission) => permissions.includes(permission),
  );
export const isEmployeeImportCursor = (after: string | null): boolean =>
  after === null || isUuid(after);
export const isEmployeeImportRowCursor = (after: string | null): boolean =>
  after === null || (/^(0|[1-9]\d{0,3})$/u.test(after) && Number(after) <= 5000);

export const employeeImportScopeLost = (failure: Failure): boolean =>
  [
    "employee_import_access_required",
    "employee_import_not_found",
    "access_denied",
    "company_access_denied",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
  ].includes(failure.code);
