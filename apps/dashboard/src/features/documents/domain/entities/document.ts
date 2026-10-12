import type { CompanyId } from "../../../../core/domain/identifiers";

export type Document = Readonly<{
  id: string;
  companyId: CompanyId;
  employmentId: string;
  title: string;
  classification: "PERSONAL" | "RECEIPT" | "HR_ONLY";
  revisionCount: number;
  version: number;
  createdBy: string;
  createdAt: string;
  currentRevisionId: string | null;
}>;
export type DocumentPage = Readonly<{ items: readonly Document[]; nextCursor: string | null }>;
