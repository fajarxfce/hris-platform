import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBalanceSummary } from "./leave-balance-summary";
import type { LeaveEmployeeReference } from "./leave-employee-reference";
import type { LeaveLedgerEntry } from "./leave-ledger-entry";

export type LeaveLedger = LeaveBalanceSummary &
  Readonly<{
    companyId: CompanyId;
    employee: LeaveEmployeeReference;
    availableActions: readonly "ADJUST"[];
    entries: readonly LeaveLedgerEntry[];
    nextCursor: string | null;
  }>;
