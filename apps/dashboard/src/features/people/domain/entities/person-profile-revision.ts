import type { AccountId } from "../../../../core/domain/identifiers";
import type { PersonProfileFields } from "./person-profile";

export type PersonProfileRevision = PersonProfileFields &
  Readonly<{
    revision: number;
    accountId: AccountId | null;
    actorId: AccountId | null;
    reason: string;
    recordedAt: string;
  }>;
export type PersonProfileHistoryPage = Readonly<{
  items: readonly PersonProfileRevision[];
  nextCursor: string | null;
}>;
