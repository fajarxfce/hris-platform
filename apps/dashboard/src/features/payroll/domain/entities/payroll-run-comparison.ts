import type { PayrollPeriods } from "./payroll-period";
import type { PayrollRun } from "./payroll-run";
export type PayrollComparisonAmounts = Readonly<{
  taxableGross: string;
  withheld: string;
  takeHome: string;
}>;
export type PayrollComparisonRun = Readonly<{
  id: string;
  periodId: string;
  number: number;
  earningsMonth: string;
  status: "CALCULATED" | "FINALIZED";
  version: number;
  employees: number;
}>;
export type PayrollComparisonEmployee = Readonly<{
  number: string;
  name: string;
  amounts: PayrollComparisonAmounts;
}>;
export type PayrollComparisonItem = Readonly<{
  employmentId: string;
  baseline: PayrollComparisonEmployee | null;
  current: PayrollComparisonEmployee | null;
  change: "ADDED" | "REMOVED" | "CHANGED" | "UNCHANGED";
  delta: PayrollComparisonAmounts;
}>;
export type PayrollRunComparison = Readonly<{
  baseline: PayrollComparisonRun;
  current: PayrollComparisonRun;
  summary: Readonly<{
    baselineEmployees: number;
    currentEmployees: number;
    matched: number;
    added: number;
    removed: number;
    changed: number;
    unchanged: number;
    baseline: PayrollComparisonAmounts;
    current: PayrollComparisonAmounts;
    delta: PayrollComparisonAmounts;
  }>;
  employees: Readonly<{ items: readonly PayrollComparisonItem[]; nextCursor: string | null }>;
  changedOnly: boolean;
  observedAt: string;
}>;
export type PayrollComparisonContext = Readonly<{
  run: PayrollRun;
  month: string;
  periods: PayrollPeriods;
}>;
