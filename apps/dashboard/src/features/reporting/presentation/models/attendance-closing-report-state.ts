import type { Failure } from "../../../../core/domain/result";
import type { AttendanceClosingReport } from "../../domain/entities/attendance-closing-report";

export type AttendanceClosingReportState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: AttendanceClosingReport | null;
  failure: Failure | null;
}>;

export const initialAttendanceClosingReportState: AttendanceClosingReportState = {
  stage: "idle",
  report: null,
  failure: null,
};
