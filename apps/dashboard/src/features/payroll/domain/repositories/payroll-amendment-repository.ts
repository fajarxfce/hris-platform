import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  PayrollAmendment,
  PayrollAmendmentDetails,
  PayrollAmendmentPage,
} from "../entities/payroll-amendment";
import type { PayrollAmendmentCalculation } from "../entities/payroll-amendment-calculation";
import type { PayrollAmendmentCommand } from "../entities/payroll-amendment-command";
import type { PayrollAmendmentSave } from "../entities/payroll-amendment-preparation";
import type { PayrollAmendmentSearch } from "../entities/payroll-amendment-search";
export interface PayrollAmendmentRepository {
  calculate(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  cancel(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  save(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentSave,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    company: CompanyId,
    search: PayrollAmendmentSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPage>>;
  get(
    company: CompanyId,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentDetails>>;
  history(
    company: CompanyId,
    id: string,
    after: number,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPage>>;
  revision(
    company: CompanyId,
    id: string,
    version: number,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendment>>;
  calculation(
    company: CompanyId,
    id: string,
    calculationId: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentCalculation>>;
}
