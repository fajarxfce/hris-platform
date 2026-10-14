import type {
  PayrollInputChangeDto,
  PayrollInputDto,
  PayrollInputEmployeesDto,
  PayrollInputReceiptDto,
  PayrollInputReviewDto,
  PayrollInputVerificationDto,
} from "../models/payroll-input-dto";
export interface PayrollInputDataSource {
  employees(
    company: string,
    month: string,
    query: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollInputEmployeesDto>;
  review(
    company: string,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollInputReviewDto>;
  revision(
    company: string,
    employee: string,
    month: string,
    revision: number,
    signal: AbortSignal,
  ): Promise<PayrollInputDto>;
  save(
    company: string,
    employee: string,
    month: string,
    operation: string,
    input: PayrollInputChangeDto,
    signal: AbortSignal,
  ): Promise<PayrollInputReceiptDto>;
  verify(
    company: string,
    employee: string,
    month: string,
    operation: string,
    input: PayrollInputVerificationDto,
    signal: AbortSignal,
  ): Promise<PayrollInputReceiptDto>;
}
