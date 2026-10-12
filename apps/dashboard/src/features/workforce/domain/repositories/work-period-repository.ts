import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { WorkPeriodCommand } from "../entities/work-period-command";
import type { WorkPeriodEmployeeSearch } from "../entities/work-period-employee-search";
import type { WorkPeriodOverview } from "../entities/work-period-overview";
import type { WorkPeriodSnapshot } from "../entities/work-period-snapshot";
import type { WorkforceEmployeePage } from "../entities/workforce-employee-page";

export interface WorkPeriodRepository {
  overview(
    company: CompanyId,
    month: string,
    signal: AbortSignal,
  ): Promise<Result<WorkPeriodOverview>>;
  employees(
    company: CompanyId,
    search: WorkPeriodEmployeeSearch,
    signal: AbortSignal,
  ): Promise<Result<WorkforceEmployeePage>>;
  snapshot(
    company: CompanyId,
    month: string,
    employee: EmployeeId,
    signal: AbortSignal,
  ): Promise<Result<WorkPeriodSnapshot>>;
  close(
    company: CompanyId,
    operation: OperationId,
    command: WorkPeriodCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  recover(
    company: CompanyId,
    operation: OperationId,
    command: WorkPeriodCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
