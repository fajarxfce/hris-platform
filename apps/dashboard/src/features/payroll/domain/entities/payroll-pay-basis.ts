export const prorationBases = ["CALENDAR_DAYS", "SCHEDULED_DAYS"] as const;
export const payrollWorkWeeks = ["FIVE_DAYS", "SIX_DAYS"] as const;
export const payrollWeekdays = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;
export const overtimeEligibility = ["ELIGIBLE", "EXEMPT"] as const;
export const serviceMonthConventions = ["COMPLETE_MONTHS", "CALENDAR_FRACTION"] as const;
export const earningsRounding = ["HALF_UP", "UP"] as const;
export const overtimeRuleId = "ID-PP35-2021-v1";
export const holidayAllowanceRuleId = "ID-PERMENAKER6-2016-v1";
export type PayrollPayBasis = Readonly<{
  overtimeRuleId: string;
  holidayAllowanceRuleId: string;
  proration: (typeof prorationBases)[number];
  workWeek: (typeof payrollWorkWeeks)[number];
  shortestWorkDay: (typeof payrollWeekdays)[number] | null;
  overtimeEligibility: (typeof overtimeEligibility)[number];
  overtimeExemptionReference: string | null;
  regularNonFixedWage: string;
  serviceMonthConvention: (typeof serviceMonthConventions)[number];
  rounding: (typeof earningsRounding)[number];
  reviewReference: string;
}>;
