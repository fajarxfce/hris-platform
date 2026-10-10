import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { Employee, EmployeeId } from "../entities/employee";
import type { EmployeeCreation } from "../entities/employee-creation";
import type { EmployeePage } from "../entities/employee-page";
import type { EmployeeSearch } from "../entities/employee-search";
import type { EmploymentChange } from "../entities/employment-change";
import type { EmploymentDetails } from "../entities/employment-details";
import type { EmploymentHistoryPage } from "../entities/employment-revision";

export interface EmployeeRepository {
  details(
    companyId: CompanyId,
    id: EmployeeId,
    asOf: string,
    signal: AbortSignal,
  ): Promise<Result<EmploymentDetails>>;
  revise(
    companyId: CompanyId,
    operation: OperationId,
    change: EmploymentChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  create(
    companyId: CompanyId,
    operation: OperationId,
    input: EmployeeCreation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    companyId: CompanyId,
    search: EmployeeSearch,
    signal: AbortSignal,
  ): Promise<Result<EmployeePage>>;
  get(
    companyId: CompanyId,
    id: EmployeeId,
    asOf: string,
    signal: AbortSignal,
  ): Promise<Result<Employee>>;
  history(
    companyId: CompanyId,
    id: EmployeeId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<EmploymentHistoryPage>>;
}
