import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollCalculationOutput } from "./payroll-calculation-output";
import type { PayrollCalculationPolicy } from "./payroll-calculation-review";
import type { PayrollRunItem } from "./payroll-run-item";
export type PayrollCalculationEvidence = Readonly<{
  month: string;
  incomeDueDate: string;
  plannedPaymentDate: string;
  policy: PayrollCalculationPolicy;
  basicSalary: string;
  inputReference: string;
  openingThroughMonth: number;
  openingReference: string;
  workDays: readonly Readonly<{
    date: string;
    schedule: "WORK" | "OFF" | "UNASSIGNED";
    attendance: "WORKED" | "ABSENCE_RECORDED" | "UNRECORDED" | "OFF" | "UNASSIGNED";
    officialHoliday: boolean;
  }>[];
  leaveDays: readonly Readonly<{
    requestId: string;
    revision: number;
    date: string;
    portion: string;
    paid: boolean;
  }>[];
}>;
export type PayrollRunEmployee = Readonly<{
  companyId: CompanyId;
  runId: string;
  item: PayrollRunItem;
  evidence: PayrollCalculationEvidence | null;
  calculation: PayrollCalculationOutput | null;
}>;
