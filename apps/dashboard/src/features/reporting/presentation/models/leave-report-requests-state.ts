import type { Failure } from "../../../../core/domain/result";
import type { LeaveReportRequestPage } from "../../domain/entities/leave-report-request";

export type LeaveReportRequestsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LeaveReportRequestPage | null;
  failure: Failure | null;
}>;
export const initialLeaveReportRequestsState: LeaveReportRequestsState = {
  stage: "loading",
  page: null,
  failure: null,
};
