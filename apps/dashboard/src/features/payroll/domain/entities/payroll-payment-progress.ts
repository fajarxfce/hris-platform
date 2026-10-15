import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollSettlement } from "./payroll-settlement";

export type PayrollPaymentProgress = Readonly<{
  companyId: CompanyId;
  assessmentId: string;
  version: number;
  settlement: PayrollSettlement;
  attempts: readonly Readonly<{
    batchId: string;
    itemId: string;
    amount: string;
    assessmentRevision: number;
    status: "PREPARED" | "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
    createdAt: string;
    releasedAt: string | null;
    resolvedAt: string | null;
  }>[];
}>;
