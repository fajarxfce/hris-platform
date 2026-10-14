import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollCalculationOutput } from "./payroll-calculation-output";

export type PayrollPayslipScope = "AUTHORIZED" | "SELF";
export type PayrollDocumentLanguage = "ID" | "EN";
export type PayrollPayslipSummary = Readonly<{
  companyId: CompanyId;
  id: string;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  taxMonth: string;
  plannedPaymentDate: string;
  currency: "IDR";
  taxableGross: string;
  withheld: string;
  takeHome: string;
  publishedAt: string;
  version: number;
}>;
export type PayrollPayslip = Readonly<{
  summary: PayrollPayslipSummary;
  companyCode: string;
  companyName: string;
  calculation: PayrollCalculationOutput;
}>;
export type PayrollPayslipSearch = Readonly<{
  from: string;
  until: string;
  query: string;
  employeeId: string | null;
  scope: PayrollPayslipScope;
  after: string | null;
}>;
export type PayrollPayslipPage = Readonly<{
  companyId: CompanyId;
  scope: PayrollPayslipScope;
  items: readonly PayrollPayslipSummary[];
  nextCursor: string | null;
}>;
