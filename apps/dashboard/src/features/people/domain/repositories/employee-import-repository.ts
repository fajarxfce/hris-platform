import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeImportId, EmployeeImportPage } from "../entities/employee-import";
import type { EmployeeImportAttempts } from "../entities/employee-import-attempt";
import type {
  EmployeeImportApplication,
  EmployeeImportChange,
} from "../entities/employee-import-change";
import type { EmployeeImportRows } from "../entities/employee-import-row";
import type { EmployeeImportStart } from "../entities/employee-import-start";
import type { EmployeeImportSummary } from "../entities/employee-import-summary";

export interface EmployeeImportRepository {
  start(
    company: CompanyId,
    operation: OperationId,
    input: EmployeeImportStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  template(company: CompanyId, signal: AbortSignal): Promise<Result<string>>;
  apply(
    company: CompanyId,
    operation: OperationId,
    change: EmployeeImportApplication,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  resume(
    company: CompanyId,
    operation: OperationId,
    change: EmployeeImportChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  cancel(
    company: CompanyId,
    operation: OperationId,
    change: EmployeeImportChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
