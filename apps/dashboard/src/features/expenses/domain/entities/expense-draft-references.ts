import type { CompanyId } from "../../../../core/domain/identifiers";
export type ExpenseDraftLabel = Readonly<{
  id: string;
  code: string | null;
  name: string | null;
  active: boolean | null;
}>;
export type ExpenseDraftReceiptLabel = Readonly<{
  revisionId: string;
  documentId: string | null;
  fileName: string | null;
  mediaType: string | null;
  size: number | null;
  status: string | null;
}>;
export type ExpenseDraftReferences = Readonly<{
  companyId: CompanyId;
  employmentId: string;
  categories: readonly ExpenseDraftLabel[];
  costCenters: readonly ExpenseDraftLabel[];
  receipts: readonly ExpenseDraftReceiptLabel[];
}>;
export type ExpenseDraftReferenceQuery = Readonly<{
  categoryIds: readonly string[];
  costCenterIds: readonly string[];
  receiptRevisionIds: readonly string[];
}>;
