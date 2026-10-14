import type { BinaryFileContent } from "../../../../core/domain/files/binary-file";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { PayrollPaymentProgress } from "../entities/payroll-payment-progress";
import type {
  PayrollDocumentLanguage,
  PayrollPayslip,
  PayrollPayslipPage,
  PayrollPayslipScope,
  PayrollPayslipSearch,
} from "../entities/payroll-payslip";

export interface PayrollPayslipRepository {
  list(
    company: CompanyId,
    search: PayrollPayslipSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPayslipPage>>;
  get(
    company: CompanyId,
    id: string,
    scope: PayrollPayslipScope,
    signal: AbortSignal,
  ): Promise<Result<PayrollPayslip>>;
  payments(
    company: CompanyId,
    id: string,
    scope: PayrollPayslipScope,
    signal: AbortSignal,
  ): Promise<Result<PayrollPaymentProgress>>;
  pdf(
    company: CompanyId,
    id: string,
    scope: PayrollPayslipScope,
    language: PayrollDocumentLanguage,
    signal: AbortSignal,
  ): Promise<Result<BinaryFileContent>>;
}
