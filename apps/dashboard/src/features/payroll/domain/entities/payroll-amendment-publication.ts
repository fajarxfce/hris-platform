export type PayrollAmendmentPublication = Readonly<{
  id: string;
  amendmentId: string;
  reviewId: string;
  calculationId: string;
  amendmentVersion: number;
  reviewVersion: number;
  approvalVersion: number;
  number: number;
  jobId: string;
  actorId: string;
  requestedAt: string;
  reason: string;
  status: "PENDING" | "PUBLISHED" | "STOPPED";
  completedAt: string | null;
  failureCode: string | null;
  completedBy: string | null;
}>;
