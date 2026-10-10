export type LeavePolicyChangeDto = Readonly<{
  code: string;
  name: string;
  effectiveFrom: string;
  paid: boolean;
  allowPartialDays: boolean;
  minServiceMonths: number;
  allowedContracts: readonly ("PERMANENT" | "FIXED_TERM")[];
  maxRequestDays: number;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
  attachmentRequired: boolean;
  accrual: Readonly<{
    frequency: "MANUAL" | "MONTHLY" | "ANNUAL";
    daysPerPeriod: string;
    carryLimitDays: string;
  }> | null;
}>;
