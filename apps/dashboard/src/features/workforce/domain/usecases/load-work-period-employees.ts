import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkPeriodEmployeeSearch } from "../entities/work-period-employee-search";
import { parseWorkPeriodEmployeeSearch } from "../policies/work-period-policy";
import type { WorkPeriodRepository } from "../repositories/work-period-repository";

export class LoadWorkPeriodEmployees {
  constructor(private readonly periods: WorkPeriodRepository) {}
  execute(access: CompanyAccess, input: WorkPeriodEmployeeSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("workforce.read"))
      return Promise.resolve(failed("access_denied"));
    const search = parseWorkPeriodEmployeeSearch(input);
    return search.ok
      ? this.periods.employees(access.companyId, search.value, signal)
      : Promise.resolve(search);
  }
}
