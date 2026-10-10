import type { Failure } from "../../../../core/domain/result";
import type {
  EmploymentHistoryPage,
  EmploymentRevision,
} from "../../domain/entities/employment-revision";

export type EmploymentHistoryState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: EmploymentHistoryPage | null;
  selected: EmploymentRevision | null;
  failure: Failure | null;
}>;
export const initialEmploymentHistoryState: EmploymentHistoryState = Object.freeze({
  stage: "idle",
  page: null,
  selected: null,
  failure: null,
});
