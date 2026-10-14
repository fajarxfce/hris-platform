import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  PayrollCalculationRecovery,
  PayrollCalculationStart,
} from "../entities/payroll-calculation-command";
import type { PayrollCalculationReview } from "../entities/payroll-calculation-review";
import type { PayrollRun } from "../entities/payroll-run";
import type { PayrollRunDetails } from "../entities/payroll-run-details";
import type { PayrollRunEmployee } from "../entities/payroll-run-employee";
export interface PayrollCalculationRepository {
  review(
    company: CompanyId,
    periodId: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollCalculationReview>>;
  runs(
    company: CompanyId,
    periodId: string,
    signal: AbortSignal,
  ): Promise<Result<readonly PayrollRun[]>>;
  run(
    company: CompanyId,
    id: string,
    after: string | null,
    limit: number,
    signal: AbortSignal,
  ): Promise<Result<PayrollRunDetails>>;
  employee(
    company: CompanyId,
    id: string,
    employee: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollRunEmployee>>;
  start(
    company: CompanyId,
    periodId: string,
    operation: OperationId,
    input: PayrollCalculationStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  resume(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollCalculationRecovery,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  abandon(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollCalculationRecovery,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
