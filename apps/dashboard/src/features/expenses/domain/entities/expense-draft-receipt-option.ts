import type { CompanyId } from "../../../../core/domain/identifiers";
export type ExpenseDraftReceiptOption = Readonly<{
  companyId: CompanyId;
  employmentId: string;
  documentId: string;
  revisionId: string;
  revisionNumber: number;
  title: string;
  fileName: string;
  mediaType: string;
  size: number;
  validatedAt: string;
}>;
export type ExpenseDraftReceiptPage = Readonly<{
  items: readonly ExpenseDraftReceiptOption[];
  nextCursor: string | null;
}>;
