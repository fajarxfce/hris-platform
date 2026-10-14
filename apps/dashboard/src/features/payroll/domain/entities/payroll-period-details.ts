import type { PayrollPeriod } from "./payroll-period";
import type { PayrollPeriodChange } from "./payroll-period-change";
import type { PayrollPeriodMember } from "./payroll-period-member";
export type PayrollPeriodDetails = Readonly<{
  period: PayrollPeriod;
  members: Readonly<{ items: readonly PayrollPeriodMember[]; nextCursor: string | null }>;
  history: Readonly<{ items: readonly PayrollPeriodChange[]; nextCursor: string | null }>;
}>;
