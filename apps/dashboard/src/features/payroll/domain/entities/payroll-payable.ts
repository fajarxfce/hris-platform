import type { CompanyId } from "../../../../core/domain/identifiers";
export type PayrollPayable = Readonly<{
  companyId: CompanyId;
  assessmentId: string;
  employmentId: string;
  employeeNumber: string;
  employeeName: string;
  taxMonth: string;
  plannedPaymentDate: string;
  amount: string;
  currency: "IDR";
  publishedAt: string;
}>;
export type PayrollPayablePage = Readonly<{
  items: readonly PayrollPayable[];
  nextCursor: string | null;
}>;
