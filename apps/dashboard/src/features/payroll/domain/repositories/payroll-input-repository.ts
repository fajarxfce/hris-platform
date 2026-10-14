import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollInput } from "../entities/payroll-input";
import type {
  PayrollInputChange,
  PayrollInputVerification,
} from "../entities/payroll-input-change";
import type {
  PayrollInputEmployees,
  PayrollInputReview,
  PayrollInputSearch,
} from "../entities/payroll-input-review";
export interface PayrollInputRepository {
  employees(
    company: CompanyId,
    search: PayrollInputSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollInputEmployees>>;
  review(
    company: CompanyId,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<PayrollInputReview>>;
  revision(
    company: CompanyId,
    employee: string,
    month: string,
    revision: number,
    signal: AbortSignal,
  ): Promise<Result<PayrollInput>>;
  save(
    company: CompanyId,
    employee: string,
    month: string,
    operation: OperationId,
    input: PayrollInputChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  verify(
    company: CompanyId,
    employee: string,
    month: string,
    operation: OperationId,
    input: PayrollInputVerification,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
