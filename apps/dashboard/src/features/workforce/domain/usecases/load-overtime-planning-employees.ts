import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimePlanningSearch } from "../entities/overtime-planning-search";
import { parseOvertimePlanningSearch } from "../policies/overtime-planning-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class LoadOvertimePlanningEmployees {
  constructor(private readonly requests: OvertimeRepository) {}
  execute(access: CompanyAccess, input: OvertimePlanningSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("overtime.manage"))
      return Promise.resolve(failed("access_denied"));
    const search = parseOvertimePlanningSearch(input);
    return search.ok
      ? this.requests.planningEmployees(access.companyId, search.value, signal)
      : Promise.resolve(search);
  }
}
