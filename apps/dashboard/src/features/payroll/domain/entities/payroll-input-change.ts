import type { PayrollInputTerms } from "./payroll-input-terms";
export type PayrollInputChange = Readonly<{
  workJobId: string;
  expectedWorkPeriodVersion: number;
  expectedEmploymentVersion: number;
  expectedVersion: number | null;
  terms: PayrollInputTerms;
  reason: string;
}>;
export type PayrollInputVerification = Readonly<{ expectedVersion: number; reason: string }>;
