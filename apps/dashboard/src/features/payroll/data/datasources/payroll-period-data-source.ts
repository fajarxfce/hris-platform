import type {
  PayrollPeriodCreationDto,
  PayrollPeriodDetailsDto,
  PayrollPeriodEmployeesDto,
  PayrollPeriodReceiptDto,
  PayrollPeriodsDto,
} from "../models/payroll-period-dto";
export interface PayrollPeriodDataSource {
  list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPeriodsDto>;
  details(
    company: string,
    id: string,
    memberAfter: string | null,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPeriodDetailsDto>;
  employees(
    company: string,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPeriodEmployeesDto>;
  create(
    company: string,
    operation: string,
    input: PayrollPeriodCreationDto,
    signal: AbortSignal,
  ): Promise<PayrollPeriodReceiptDto>;
  cancel(
    company: string,
    id: string,
    operation: string,
    version: number,
    reason: string,
    signal: AbortSignal,
  ): Promise<PayrollPeriodReceiptDto>;
}
