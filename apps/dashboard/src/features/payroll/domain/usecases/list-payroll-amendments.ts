import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentPage } from "../entities/payroll-amendment";
import type { PayrollAmendmentSearch } from "../entities/payroll-amendment-search";
import {
  canReadPayrollAmendments,
  validPayrollAmendmentSearch,
} from "../policies/payroll-amendment-access-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
export class ListPayrollAmendments {
  constructor(private readonly amendments: PayrollAmendmentRepository) {}
  execute(
    access: CompanyAccess,
    search: PayrollAmendmentSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPage>> {
    signal.throwIfAborted();
    if (!canReadPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!validPayrollAmendmentSearch(search)) return Promise.resolve(failed("invalid_page"));
    return this.amendments.list(
      access.companyId,
      {
        ...search,
        query: search.query.trim(),
        employeeId: search.employeeId?.toLowerCase() ?? null,
        after: search.after?.toLowerCase() ?? null,
      },
      signal,
    );
  }
}
