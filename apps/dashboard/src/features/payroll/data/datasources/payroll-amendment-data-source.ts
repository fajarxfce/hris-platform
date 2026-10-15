import type {
  PayrollAmendmentCalculationDto,
  PayrollAmendmentDetailsDto,
  PayrollAmendmentDto,
  PayrollAmendmentHistoryDto,
  PayrollAmendmentPageDto,
  PayrollAmendmentSearchDto,
} from "../models/payroll-amendment-dto";
export interface PayrollAmendmentDataSource {
  list(
    company: string,
    search: PayrollAmendmentSearchDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentPageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<PayrollAmendmentDetailsDto>;
  history(
    company: string,
    id: string,
    after: number,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentHistoryDto>;
  revision(
    company: string,
    id: string,
    version: number,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentDto>;
  calculation(
    company: string,
    id: string,
    calculationId: string,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentCalculationDto>;
}
