import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type {
  PayrollDocumentLanguageDto,
  PayrollPaymentProgressDto,
  PayrollPayslipDto,
  PayrollPayslipPageDto,
  PayrollPayslipScopeDto,
  PayrollPayslipSearchDto,
} from "../models/payroll-payslip-dto";

export interface PayrollPayslipDataSource {
  list(
    company: string,
    search: PayrollPayslipSearchDto,
    signal: AbortSignal,
  ): Promise<PayrollPayslipPageDto>;
  get(
    company: string,
    id: string,
    scope: PayrollPayslipScopeDto,
    revision: number | null,
    signal: AbortSignal,
  ): Promise<PayrollPayslipDto>;
  payments(
    company: string,
    id: string,
    scope: PayrollPayslipScopeDto,
    signal: AbortSignal,
  ): Promise<PayrollPaymentProgressDto>;
  pdf(
    company: string,
    id: string,
    scope: PayrollPayslipScopeDto,
    revision: number,
    language: PayrollDocumentLanguageDto,
    signal: AbortSignal,
  ): Promise<BinaryResponseDto>;
}
