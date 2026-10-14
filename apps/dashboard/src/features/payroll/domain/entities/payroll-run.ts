import type { CompanyId } from "../../../../core/domain/identifiers";
export const payrollRunStatuses = [
  "PROCESSING",
  "STOPPED",
  "CALCULATED",
  "FINALIZED",
  "ABANDONED",
] as const;
export type PayrollRun = Readonly<{
  companyId: CompanyId;
  id: string;
  periodId: string;
  number: number;
  earningsMonth: string;
  taxMonth: string;
  incomeDueDate: string;
  plannedPaymentDate: string;
  timezone: string;
  workJobId: string;
  workPeriodVersion: number;
  policyRevision: number;
  totalEmployees: number;
  actorId: string;
  reviewReference: string;
  reason: string;
  createdAt: string;
  jobId: string;
  status: (typeof payrollRunStatuses)[number];
  version: number;
  processed: number;
  succeeded: number;
  failed: number;
  finalizationId: string | null;
}>;
