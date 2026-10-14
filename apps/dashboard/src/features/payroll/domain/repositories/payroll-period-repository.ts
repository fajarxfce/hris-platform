import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollPeriods } from "../entities/payroll-period";
import type { PayrollPeriodCreation } from "../entities/payroll-period-creation";
import type { PayrollPeriodDetails } from "../entities/payroll-period-details";
import type { PayrollPeriodEmployees } from "../entities/payroll-period-employee";
import type { PayrollPeriodSearch } from "../entities/payroll-period-search";
export interface PayrollPeriodRepository {
  list(
    company: CompanyId,
    search: PayrollPeriodSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPeriods>>;
  details(
    company: CompanyId,
    id: string,
    memberAfter: string | null,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<PayrollPeriodDetails>>;
  employees(
    company: CompanyId,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<PayrollPeriodEmployees>>;
  create(
    company: CompanyId,
    operation: OperationId,
    input: PayrollPeriodCreation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  cancel(
    company: CompanyId,
    id: string,
    operation: OperationId,
    version: number,
    reason: string,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
