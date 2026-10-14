import type { PayrollRun } from "./payroll-run";
export const payrollPublicationStatuses = ["CALCULATED", "FINALIZED"] as const;
export type PayrollPublicationSearch = Readonly<{
  from: string;
  until: string;
  status: (typeof payrollPublicationStatuses)[number] | null;
  after: string | null;
}>;
export type PayrollPublicationPage = Readonly<{
  items: readonly PayrollRun[];
  nextCursor: string | null;
}>;
