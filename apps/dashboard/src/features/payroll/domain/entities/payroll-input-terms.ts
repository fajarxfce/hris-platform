export const payrollDayPortions = ["FULL", "FIRST_HALF", "SECOND_HALF"] as const;
export const payrollDayDispositions = ["PAID", "UNPAID"] as const;
export const payrollHolidayKinds = [
  "EID_FITR",
  "CHRISTMAS",
  "NYEPI",
  "VESAK",
  "LUNAR_NEW_YEAR",
] as const;
export const payrollPriorHolidayPayments = ["NOT_PAID", "PAID"] as const;
export type PayrollVariableEarning = Readonly<{
  code: string;
  name: string;
  amount: string;
  taxable: boolean;
}>;
export type PayrollNetDeduction = Readonly<{ code: string; name: string; amount: string }>;
export type PayrollDayResolution = Readonly<{
  workDate: string;
  portion: (typeof payrollDayPortions)[number];
  disposition: (typeof payrollDayDispositions)[number];
  reference: string;
}>;
export type PayrollHolidayInput = Readonly<{
  kind: (typeof payrollHolidayKinds)[number];
  holidayDate: string;
  continuousServiceFrom: string;
  priorPayment: (typeof payrollPriorHolidayPayments)[number];
  reviewReference: string;
  promisedAmount: string | null;
}>;
export type PayrollInputTerms = Readonly<{
  earnings: readonly PayrollVariableEarning[];
  deductions: readonly PayrollNetDeduction[];
  nonCashTaxable: string;
  scheduledMonthUnits: string | null;
  dayResolutions: readonly PayrollDayResolution[];
  holidayAllowance: PayrollHolidayInput | null;
  reviewReference: string;
}>;
