import type { AccountId } from "../../../../core/domain/identifiers";

export type EmployeeImportAttempt = Readonly<{
  jobId: string;
  phase: "PREVIEW" | "APPLY";
  actorId: AccountId;
  createdAt: string;
}>;
export type EmployeeImportAttempts = Readonly<{
  items: readonly EmployeeImportAttempt[];
  nextCursor: string | null;
}>;
