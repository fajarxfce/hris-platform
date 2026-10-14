import type { PayrollPeriodStatus } from "./payroll-period";
export type PayrollPeriodSearch = Readonly<{
  from: string;
  until: string;
  status: PayrollPeriodStatus | null;
  after: string | null;
}>;
