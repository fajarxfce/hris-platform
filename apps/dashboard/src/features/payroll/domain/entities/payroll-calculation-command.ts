export type PayrollCalculationFields = Readonly<{
  incomeDueDate: string;
  reviewReference: string;
  reason: string;
}>;
export type PayrollCalculationStart = PayrollCalculationFields &
  Readonly<{
    id: string;
    expectedPeriodVersion: number;
    expectedWorkPeriodVersion: number;
    expectedPolicyVersion: number;
  }>;
export type PayrollCalculationRecoveryAction = "resume" | "abandon";
export type PayrollCalculationRecovery = Readonly<{
  expectedVersion: number;
  expectedPeriodVersion: number;
  reason: string;
}>;
