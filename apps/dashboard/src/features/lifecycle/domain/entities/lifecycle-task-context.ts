import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId, LifecycleCaseStatus } from "./lifecycle-case";
import type { LifecycleEmployeeReference } from "./lifecycle-employee-reference";
import type { LifecycleTask } from "./lifecycle-task";
import type { LifecycleKind } from "./lifecycle-template";

/** An observed task and case version; later reads cannot silently retarget a command. */
export type LifecycleTaskContext = Readonly<{
  companyId: CompanyId;
  caseId: LifecycleCaseId;
  caseVersion: number;
  caseStatus: LifecycleCaseStatus;
  employee: LifecycleEmployeeReference;
  kind: LifecycleKind;
  task: LifecycleTask;
}>;
export type AssignedLifecycleTaskPage = Readonly<{
  items: readonly LifecycleTaskContext[];
  nextCursor: string | null;
}>;
