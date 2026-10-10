import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportAction } from "../entities/employee-import-change";
import type { EmployeeImportSummary } from "../entities/employee-import-summary";
import { canImportEmployees } from "./employee-import-policy";

export function validateEmployeeImportReview(
  access: CompanyAccess,
  review: EmployeeImportSummary,
  action: EmployeeImportAction,
  allowPartial = false,
): Result<void> {
  if (!canImportEmployees(access.permissions) || review.batch.companyId !== access.companyId)
    return failed("employee_import_access_required");
  if (!review.availableActions.includes(action) || review.batch.version === Number.MAX_SAFE_INTEGER)
    return failed("employee_import_action_unavailable");
  if (action === "apply" && review.counts.INVALID > 0 && !allowPartial)
    return failed("employee_import_has_invalid_rows");
  return success(undefined);
}
