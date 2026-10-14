import type { CompanyId } from "../../../../core/domain/identifiers";

export type PayrollPaymentProgress = Readonly<{
  companyId: CompanyId;
  assessmentId: string;
  version: number;
  attempts: readonly Readonly<{
    batchId: string;
    itemId: string;
    status: "PREPARED" | "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
    createdAt: string;
    releasedAt: string | null;
    resolvedAt: string | null;
  }>[];
}>;
