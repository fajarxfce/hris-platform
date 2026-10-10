import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import { isEmployeeId } from "../policies/employee-policy";
import { canReadProfileHistory, isProfileRevisionCursor } from "../policies/person-profile-policy";
import type { PersonProfileRepository } from "../repositories/person-profile-repository";

export class LoadPersonProfileHistory {
  constructor(private readonly profiles: PersonProfileRepository) {}
  execute(access: CompanyAccess, employeeId: string, after: string | null, signal: AbortSignal) {
    if (!canReadProfileHistory(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isEmployeeId(employeeId)) return Promise.resolve(failed("person_profile_not_found"));
    if (after !== null && !isProfileRevisionCursor(after))
      return Promise.resolve(failed("invalid_page"));
    return this.profiles.history(
      access.companyId,
      employeeId.toLowerCase() as EmployeeId,
      after,
      signal,
    );
  }
}
