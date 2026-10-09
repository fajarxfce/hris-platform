import type { Failure } from "../../../../core/domain/result";
import type { HeadcountReport } from "../../domain/entities/headcount-report";

export type HeadcountState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: HeadcountReport | null;
  failure: Failure | null;
}>;

export const initialHeadcountState: HeadcountState = { stage: "idle", report: null, failure: null };
