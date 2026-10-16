import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollInputTerms } from "./payroll-input-terms";
export const payrollInputStatuses = ["DRAFT", "VERIFIED"] as const;
export type PayrollInputStatus = (typeof payrollInputStatuses)[number];
export type PayrollInputSummary = Readonly<{
  id: string;
  employeeId: string;
  earningsMonth: string;
  version: number;
  status: PayrollInputStatus;
  preparedBy: string;
  verifiedBy: string | null;
  recordedAt: string;
  reason: string;
}>;
export type PayrollInput = PayrollInputSummary &
  Readonly<{
    companyId: CompanyId;
    workJobId: string;
    workPeriodVersion: number;
    workAdjustmentId: string | null;
    employmentVersion: number;
    terms: PayrollInputTerms;
  }>;
