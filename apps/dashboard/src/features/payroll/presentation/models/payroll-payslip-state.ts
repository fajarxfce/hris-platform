import type { Failure } from "../../../../core/domain/result";
import type { PayrollPaymentProgress } from "../../domain/entities/payroll-payment-progress";
import type {
  PayrollDocumentLanguage,
  PayrollPayslip,
  PayrollPayslipPage,
} from "../../domain/entities/payroll-payslip";

export type PayrollPayslipsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: PayrollPayslipPage | null;
  failure: Failure | null;
}>;
export const initialPayrollPayslipsState: PayrollPayslipsState = {
  stage: "loading",
  page: null,
  failure: null,
};
export type PayrollPayslipState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  payslip: PayrollPayslip | null;
  failure: Failure | null;
  payments: PayrollPaymentProgress | null;
  paymentsLoading: boolean;
  paymentFailure: Failure | null;
  download: "idle" | "saving" | "saved" | "failed";
  downloadFailure: Failure | null;
  documentLanguage: PayrollDocumentLanguage;
}>;
export const initialPayrollPayslipState: PayrollPayslipState = {
  stage: "loading",
  payslip: null,
  failure: null,
  payments: null,
  paymentsLoading: false,
  paymentFailure: null,
  download: "idle",
  downloadFailure: null,
  documentLanguage: "ID",
};
