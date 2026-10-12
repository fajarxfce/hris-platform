import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeSearchInput } from "../entities/overtime-search";
import {
  canBrowseCompanyOvertime,
  canBrowseEmployeeOvertime,
  parseOvertimeSearch,
} from "../policies/overtime-read-policy";
import type { OvertimeRepository } from "../repositories/overtime-repository";

export class LoadOvertimeRequests {
  constructor(private readonly overtime: OvertimeRepository) {}
  async execute(access: CompanyAccess, input: OvertimeSearchInput, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canBrowseEmployeeOvertime(access.permissions)) return failed("access_denied");
    if (input.employeeId === null && !canBrowseCompanyOvertime(access.permissions))
      return failed("employee_scope_required");
    const search = parseOvertimeSearch(input);
    if (!search.ok) return search;
    const result = await this.overtime.list(access.companyId, search.value, signal);
    signal.throwIfAborted();
    return result;
  }
}
