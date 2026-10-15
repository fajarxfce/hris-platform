import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentSourceSearch } from "../entities/payroll-amendment-preparation";
import { validPayrollAmendmentSearch } from "../policies/payroll-amendment-access-policy";
import { canAuthorPayrollAmendments } from "../policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentSourceRepository } from "../repositories/payroll-amendment-source-repository";
export class ListPayrollAmendmentSources {
  constructor(private readonly sources: PayrollAmendmentSourceRepository) {}
  execute(access: CompanyAccess, search: PayrollAmendmentSourceSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!validPayrollAmendmentSearch({ ...search, status: null }))
      return Promise.resolve(failed("invalid_page"));
    return this.sources.list(
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
