import type {
  PayrollCalculationReceiptDto,
  PayrollCalculationRecoveryDto,
  PayrollCalculationReviewDto,
  PayrollCalculationStartDto,
  PayrollRunDetailsDto,
  PayrollRunEmployeeDto,
  PayrollRunsDto,
} from "../models/payroll-calculation-dto";
export interface PayrollCalculationDataSource {
  review(
    company: string,
    period: string,
    signal: AbortSignal,
  ): Promise<PayrollCalculationReviewDto>;
  runs(company: string, period: string, signal: AbortSignal): Promise<PayrollRunsDto>;
  run(
    company: string,
    id: string,
    after: string | null,
    limit: number,
    signal: AbortSignal,
  ): Promise<PayrollRunDetailsDto>;
  employee(
    company: string,
    id: string,
    employee: string,
    signal: AbortSignal,
  ): Promise<PayrollRunEmployeeDto>;
  start(
    company: string,
    period: string,
    operation: string,
    input: PayrollCalculationStartDto,
    signal: AbortSignal,
  ): Promise<PayrollCalculationReceiptDto>;
  resume(
    company: string,
    id: string,
    operation: string,
    input: PayrollCalculationRecoveryDto,
    signal: AbortSignal,
  ): Promise<PayrollCalculationReceiptDto>;
  abandon(
    company: string,
    id: string,
    operation: string,
    input: PayrollCalculationRecoveryDto,
    signal: AbortSignal,
  ): Promise<PayrollCalculationReceiptDto>;
}
