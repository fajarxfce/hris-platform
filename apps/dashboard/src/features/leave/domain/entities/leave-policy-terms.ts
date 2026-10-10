export type LeavePolicyTerms = Readonly<{
  name: string;
  paid: boolean;
  allowPartialDays: boolean;
  minServiceMonths: number;
  allowedContracts: readonly ("PERMANENT" | "FIXED_TERM")[];
  maxRequestDays: number;
  attachmentRequired: boolean;
  accrual: Readonly<{
    frequency: "MANUAL" | "MONTHLY" | "ANNUAL";
    daysPerPeriod: string;
    carryLimitDays: string;
  }> | null;
}>;
