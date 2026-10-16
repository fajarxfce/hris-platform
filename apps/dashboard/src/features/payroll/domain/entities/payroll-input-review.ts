import type { PayrollInput, PayrollInputSummary } from "./payroll-input";
import type { PayrollWorkAdjustment } from "./payroll-work-adjustment";
export type PayrollInputEmployeeReference = Readonly<{
  id: string;
  employeeNumber: string;
  name: string;
  employmentVersion: number;
}>;
export const payrollInputEmployeeStatuses = ["UNPREPARED", "DRAFT", "VERIFIED"] as const;
export const payrollInputActions = ["PREPARE", "VERIFY"] as const;
export type PayrollInputAction = (typeof payrollInputActions)[number];
export type PayrollInputEmployeeStatus = (typeof payrollInputEmployeeStatuses)[number];
export type PayrollInputEmployee = PayrollInputEmployeeReference &
  Readonly<{
    inputVersion: number | null;
    inputStatus: "DRAFT" | "VERIFIED" | null;
  }>;
export type PayrollInputSearch = Readonly<{
  month: string;
  query: string;
  status: PayrollInputEmployeeStatus | null;
  after: string | null;
}>;
export type PayrollInputEmployees = Readonly<{
  items: readonly PayrollInputEmployee[];
  nextCursor: string | null;
}>;
export type PayrollInputWorkSource = Readonly<{
  periodId: string;
  earningsMonth: string;
  version: number;
  closed: boolean;
  jobId: string | null;
  includesEmployee: boolean;
  adjustment: PayrollWorkAdjustment | null;
}>;
export type PayrollInputReview = Readonly<{
  earningsMonth: string;
  employee: PayrollInputEmployeeReference;
  source: PayrollInputWorkSource | null;
  current: PayrollInput | null;
  history: Readonly<{ items: readonly PayrollInputSummary[]; nextCursor: string | null }>;
  frozen: boolean;
  availableActions: readonly PayrollInputAction[];
}>;
