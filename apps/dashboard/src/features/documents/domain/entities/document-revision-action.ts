import type { CompanyId } from "../../../../core/domain/identifiers";

export const documentRevisionActions = ["UPLOAD_CONTENT", "VALIDATE", "CANCEL_UPLOAD"] as const;
export type DocumentRevisionAction = (typeof documentRevisionActions)[number];
export const documentRevisionIntents = ["validate", "cancel"] as const;
export type DocumentRevisionIntent = (typeof documentRevisionIntents)[number];
export type DocumentRevisionSnapshot = Readonly<{
  id: string;
  documentId: string;
  companyId: CompanyId;
  version: number;
  availableActions: readonly DocumentRevisionAction[];
}>;
export type DocumentRevisionChange = Readonly<{ id: string; version: number; reason: string }>;
