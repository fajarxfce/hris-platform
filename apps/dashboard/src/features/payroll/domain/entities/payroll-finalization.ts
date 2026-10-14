import type { CompanyId } from "../../../../core/domain/identifiers";
export type PayrollFinalization = Readonly<{
  companyId: CompanyId;
  id: string;
  runId: string;
  reviewId: string;
  runVersion: number;
  reviewVersion: number;
  approvalVersion: number;
  number: number;
  jobId: string;
  actorId: string;
  requestedAt: string;
  reason: string;
  companyCode: string;
  companyName: string;
  publishedAt: string | null;
}>;
