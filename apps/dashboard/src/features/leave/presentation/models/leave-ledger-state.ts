import type { Failure } from "../../../../core/domain/result";
import type { LeaveLedger } from "../../domain/entities/leave-ledger";
import type { LeaveLedgerEntry } from "../../domain/entities/leave-ledger-entry";

export type LeaveLedgerState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  ledger: LeaveLedger | null;
  selected: LeaveLedgerEntry | null;
  failure: Failure | null;
}>;
export const initialLeaveLedgerState: LeaveLedgerState = {
  stage: "loading",
  ledger: null,
  selected: null,
  failure: null,
};
