import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { Employee, EmployeeId } from "../entities/employee";
import type { EmployeePage } from "../entities/employee-page";
import type { EmployeeSearch } from "../entities/employee-search";
import type { EmploymentHistoryPage } from "../entities/employment-revision";

export interface EmployeeRepository {
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
