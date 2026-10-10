import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleEmployeeReference } from "./lifecycle-employee-reference";
import type { LifecycleTask } from "./lifecycle-task";
import type { LifecycleKind, LifecycleTemplateId } from "./lifecycle-template";

export const lifecycleCaseStatuses = ["OPEN", "COMPLETED", "CANCELLED"] as const;
export type LifecycleCaseStatus = (typeof lifecycleCaseStatuses)[number];
export type LifecycleCaseId = string & { readonly lifecycleCaseId: unique symbol };
export type LifecycleCase = Readonly<{
  id: LifecycleCaseId;
  companyId: CompanyId;
  employee: LifecycleEmployeeReference;
  kind: LifecycleKind;
  targetDate: string;
  templateId: LifecycleTemplateId;
  templateVersion: number;
  templateName: string;
  status: LifecycleCaseStatus;
  version: number;
  createdBy: AccountId;
  createdAt: string;
  tasks: readonly LifecycleTask[];
}>;
export type LifecycleCasePage = Readonly<{
  items: readonly LifecycleCase[];
  nextCursor: string | null;
}>;
