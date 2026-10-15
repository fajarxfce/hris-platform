import { isUuid } from "../../../../core/domain/identifiers";
import { payrollAmendmentStatuses } from "../entities/payroll-amendment";
import type { PayrollAmendmentSearch } from "../entities/payroll-amendment-search";
export function canReadPayrollAmendments(permissions: readonly string[]): boolean {
  return ["payroll.amend", "payroll.amend.review", "payroll.amend.publish"].some((permission) =>
    permissions.includes(permission),
  );
}
export function validPayrollAmendmentSearch(search: PayrollAmendmentSearch): boolean {
  return (
    Number.isInteger(search.year) &&
    search.year >= 2024 &&
    search.year <= 2100 &&
    search.query.length <= 120 &&
    !/[\p{Cc}]/u.test(search.query) &&
    (search.employeeId === null || isUuid(search.employeeId)) &&
    (search.after === null || isUuid(search.after)) &&
    (search.status === null || payrollAmendmentStatuses.includes(search.status))
  );
}
export function validPayrollAmendmentRevision(version: number | null): boolean {
  return version === null || (Number.isInteger(version) && version >= 0 && version <= 999);
}
