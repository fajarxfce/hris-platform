import type { CompanyId } from "../../../../core/domain/identifiers";
export const payrollPeriodStatuses = [
  "DRAFT",
  "PROCESSING",
  "CALCULATED",
  "FINALIZED",
  "CANCELLED",
] as const;
export type PayrollPeriodStatus = (typeof payrollPeriodStatuses)[number];
export type PayrollPeriod = Readonly<{
  companyId: CompanyId;
  id: string;
  earningsMonth: string;
  plannedPaymentMonth: string;
  plannedPaymentDate: string;
  timezone: string;
  participantCount: number;
  authorId: string;
  createdAt: string;
  status: PayrollPeriodStatus;
  version: number;
  currentRunId: string | null;
}>;
export type PayrollPeriods = Readonly<{
  items: readonly PayrollPeriod[];
  nextCursor: string | null;
}>;
