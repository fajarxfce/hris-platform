import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../entities/employee";
import type { PersonAccountBinding } from "../entities/person-account-binding";
import type { PersonProfile, PersonProfileChange } from "../entities/person-profile";
import type { PersonProfileHistoryPage } from "../entities/person-profile-revision";

export interface PersonProfileRepository {
  bindAccount(
    companyId: CompanyId,
    employeeId: EmployeeId,
    operation: OperationId,
    binding: PersonAccountBinding,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  get(
    companyId: CompanyId,
    employeeId: EmployeeId,
    signal: AbortSignal,
  ): Promise<Result<PersonProfile>>;
  history(
    companyId: CompanyId,
    employeeId: EmployeeId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<PersonProfileHistoryPage>>;
  save(
    companyId: CompanyId,
    employeeId: EmployeeId,
    operation: OperationId,
    change: PersonProfileChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
