import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import { isEmployeeId } from "../policies/employee-policy";
import { canReadPersonProfile } from "../policies/person-profile-policy";
import type { PersonProfileRepository } from "../repositories/person-profile-repository";

export class LoadPersonProfile {
  constructor(private readonly profiles: PersonProfileRepository) {}
  execute(access: CompanyAccess, employeeId: string, signal: AbortSignal) {
    if (!canReadPersonProfile(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isEmployeeId(employeeId)) return Promise.resolve(failed("person_profile_not_found"));
    return this.profiles.get(access.companyId, employeeId.toLowerCase() as EmployeeId, signal);
  }
}
