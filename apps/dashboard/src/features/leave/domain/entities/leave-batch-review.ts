import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBatchKind } from "./leave-batch";
import type { LeavePolicyDefinition } from "./leave-policy-definition";
export type LeaveBatchSelection = Readonly<{
  kind: LeaveBatchKind;
  typeId: string;
  period: string;
}>;
export type LeaveBatchReview = Readonly<
  LeaveBatchSelection & {
    companyId: CompanyId;
    companyDate: string;
    timezone: string;
    type: Readonly<LeavePolicyDefinition & { appliedRevision: number }>;
    employeeIds: readonly string[];
  }
>;
