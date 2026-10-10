import type { Failure } from "../../../../core/domain/result";
import type {
  PersonProfileHistoryPage,
  PersonProfileRevision,
} from "../../domain/entities/person-profile-revision";

export type PersonProfileHistoryState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: PersonProfileHistoryPage | null;
  selected: PersonProfileRevision | null;
  failure: Failure | null;
}>;
export const initialPersonProfileHistoryState: PersonProfileHistoryState = {
  stage: "loading",
  page: null,
  selected: null,
  failure: null,
};
