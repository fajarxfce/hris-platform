import type {
  EmployeeImportAttemptsDto,
  EmployeeImportPageDto,
  EmployeeImportRowsDto,
  EmployeeImportSummaryDto,
} from "../models/employee-import-dto";

export interface EmployeeImportDataSource {
  list(company: string, after: string | null, signal: AbortSignal): Promise<EmployeeImportPageDto>;
  summary(company: string, id: string, signal: AbortSignal): Promise<EmployeeImportSummaryDto>;
  rows(
    company: string,
    id: string,
    after: number | null,
    signal: AbortSignal,
  ): Promise<EmployeeImportRowsDto>;
  attempts(
    company: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<EmployeeImportAttemptsDto>;
}
