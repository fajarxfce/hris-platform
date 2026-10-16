import type { PayrollInputTerms } from "./payroll-input-terms";
export type PayrollInputChange = Readonly<{
  workJobId: string;
  workAdjustmentId: string | null;
  expectedWorkPeriodVersion: number;
  expectedEmploymentVersion: number;
  expectedVersion: number | null;
  terms: PayrollInputTerms;
  reason: string;
}>;
export type PayrollInputVerification = Readonly<{ expectedVersion: number; reason: string }>;
