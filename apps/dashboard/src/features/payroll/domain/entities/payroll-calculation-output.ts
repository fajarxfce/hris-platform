export type PayrollCalculationOutput = Readonly<{
  units: Readonly<{
    total: string;
    payable: string;
    unpaid: string;
    employedDates: readonly string[];
  }>;
  earnings: readonly Readonly<{
    kind: "BASIC" | "FIXED" | "VARIABLE" | "OVERTIME" | "HOLIDAY";
    code: string;
    name: string | null;
    amount: string;
    taxable: boolean;
  }>[];
  overtime: readonly Readonly<{
    date: string;
    calculation: Readonly<{
      ruleId: string;
      dayKind: string;
      displayHourlyWage: string;
      amount: string;
      segments: readonly Readonly<{ minutes: number; multiplier: string }>[];
    }>;
  }>[];
  holiday: Readonly<{
    ruleId: string;
    holidayDate: string;
    dueDate: string;
    monthlyWage: string;
    statutoryAmount: string;
    employerTopUp: string;
    amount: string;
  }> | null;
  contributions: readonly Readonly<{
    program: string;
    base: string;
    employeeAmount: string;
    employerAmount: string;
  }>[];
  tax: Readonly<{
    ruleId: string;
    taxableGross: string;
    taxAllowance: string;
    deductionAllowance: string;
    withheld: string;
    takeHome: string;
    category: string | null;
    effectiveRate: string | null;
    annualNet: string | null;
    annualizedNet: string | null;
    annualTaxable: string | null;
    annualTax: string | null;
  }>;
  employeeDeductions: string;
  employerCost: string;
}>;
