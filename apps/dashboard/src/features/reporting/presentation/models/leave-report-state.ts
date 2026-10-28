import type { Failure } from "../../../../core/domain/result";
import type { LeaveReport } from "../../domain/entities/leave-report";

export type LeaveReportState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: LeaveReport | null;
  failure: Failure | null;
}>;

export const initialLeaveReportState: LeaveReportState = {
  stage: "idle",
  report: null,
  failure: null,
};
