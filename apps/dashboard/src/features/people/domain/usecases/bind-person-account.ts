import { type AccountId, isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import type { PersonAccountBinding } from "../entities/person-account-binding";
import { canLinkPersonAccounts } from "../policies/person-account-binding-policy";
import type { PersonProfileRepository } from "../repositories/person-profile-repository";

export class BindPersonAccount {
  constructor(private readonly profiles: PersonProfileRepository) {}
  execute(
    access: CompanyAccess,
    authorId: AccountId,
    employeeId: string,
    operation: OperationId,
    input: PersonAccountBinding,
    signal: AbortSignal,
  ) {
    if (!canLinkPersonAccounts(access.permissions))
      return Promise.resolve(failed("person_account_link_access_required"));
    if (access.companyId !== input.ownerCompanyId)
      return Promise.resolve(failed("profile_owner_required"));
    if (input.accountId.toLowerCase() === authorId.toLowerCase())
      return Promise.resolve(failed("independent_account_binding_required"));
    const reason = input.reason.trim();
    if (
      ![
        authorId,
        employeeId,
        operation,
        input.personId,
        input.accountId,
        input.ownerCompanyId,
      ].every(isUuid) ||
      !Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion >= Number.MAX_SAFE_INTEGER ||
      reason.length < 1 ||
      reason.length > 1000
    )
      return Promise.resolve(failed("invalid_account_binding"));
    return this.profiles.bindAccount(
      access.companyId,
      employeeId.toLowerCase() as EmployeeId,
      operation,
      Object.freeze({ ...input, accountId: input.accountId.toLowerCase() as AccountId, reason }),
      signal,
    );
  }
}
