import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBatchId, LeaveBatchKind } from "./leave-batch";
import type { LeaveBatchSelection } from "./leave-batch-review";
export type LeaveBatchCreation = Readonly<
  LeaveBatchSelection & {
    companyId: CompanyId;
    id: LeaveBatchId;
    expectedPolicyVersion: number;
    employeeIds: readonly string[];
    reason: string;
  }
>;
export type LeaveBatchResume = Readonly<{
  companyId: CompanyId;
  id: LeaveBatchId;
  kind: LeaveBatchKind;
  expectedVersion: number;
  reason: string;
}>;
