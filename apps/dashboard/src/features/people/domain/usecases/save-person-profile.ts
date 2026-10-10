import type { OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import type { PersonProfileChange } from "../entities/person-profile";
import { isEmployeeId } from "../policies/employee-policy";
import {
  canReadPersonProfile,
  normalizePersonProfileChange,
} from "../policies/person-profile-policy";
import type { PersonProfileRepository } from "../repositories/person-profile-repository";

export class SavePersonProfile {
  constructor(private readonly profiles: PersonProfileRepository) {}
  execute(
    access: CompanyAccess,
    employeeId: string,
    operation: OperationId,
    input: PersonProfileChange,
    signal: AbortSignal,
  ) {
    if (
      !canReadPersonProfile(access.permissions) ||
      !access.permissions.includes("people.profile.manage")
    )
      return Promise.resolve(failed("access_denied"));
    if (access.companyId !== input.ownerCompanyId)
      return Promise.resolve(failed("profile_owner_required"));
    if (!isEmployeeId(employeeId) || !isEmployeeId(operation))
      return Promise.resolve(failed("invalid_profile_change"));
    const normalized = normalizePersonProfileChange(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.profiles.save(
      access.companyId,
      employeeId.toLowerCase() as EmployeeId,
      operation,
      normalized.value,
      signal,
    );
  }
}
