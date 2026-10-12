import type { CompanyId } from "../../../../core/domain/identifiers";

export type DocumentRevisionAction = "VALIDATE" | "CANCEL_UPLOAD";
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
