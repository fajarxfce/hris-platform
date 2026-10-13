import type { CompanyId } from "../../../../core/domain/identifiers";

export type LeaveEvidenceOption = Readonly<{
  companyId: CompanyId;
  employeeId: string;
  documentId: string;
  revisionId: string;
  revisionNumber: number;
  title: string;
  fileName: string;
  mediaType: string;
  size: number;
  validatedAt: string;
}>;
export type LeaveEvidenceOptions = Readonly<{
  items: readonly LeaveEvidenceOption[];
  nextCursor: string | null;
}>;
