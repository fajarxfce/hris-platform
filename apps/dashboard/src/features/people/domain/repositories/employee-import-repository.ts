import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeImportId, EmployeeImportPage } from "../entities/employee-import";
import type { EmployeeImportAttempts } from "../entities/employee-import-attempt";
import type { EmployeeImportRows } from "../entities/employee-import-row";
import type { EmployeeImportSummary } from "../entities/employee-import-summary";

export interface EmployeeImportRepository {
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<EmployeeImportPage>>;
  summary(
    company: CompanyId,
    id: EmployeeImportId,
    signal: AbortSignal,
  ): Promise<Result<EmployeeImportSummary>>;
  rows(
    company: CompanyId,
    id: EmployeeImportId,
    after: number | null,
    signal: AbortSignal,
  ): Promise<Result<EmployeeImportRows>>;
  attempts(
    company: CompanyId,
    id: EmployeeImportId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<EmployeeImportAttempts>>;
}
