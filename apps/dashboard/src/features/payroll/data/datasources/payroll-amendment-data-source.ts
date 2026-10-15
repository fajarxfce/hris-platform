import type {
  PayrollAmendmentCommandDto,
  PayrollAmendmentReceiptDto,
  PayrollAmendmentSaveDto,
} from "../models/payroll-amendment-command-dto";
import type {
  PayrollAmendmentCalculationDto,
  PayrollAmendmentDetailsDto,
  PayrollAmendmentDto,
  PayrollAmendmentHistoryDto,
  PayrollAmendmentPageDto,
  PayrollAmendmentSearchDto,
} from "../models/payroll-amendment-dto";
export interface PayrollAmendmentDataSource {
  calculate(
    company: string,
    id: string,
    operation: string,
    input: PayrollAmendmentCommandDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
  cancel(
    company: string,
    id: string,
    operation: string,
    input: PayrollAmendmentCommandDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
  save(
    company: string,
    id: string,
    operation: string,
    input: PayrollAmendmentSaveDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
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
