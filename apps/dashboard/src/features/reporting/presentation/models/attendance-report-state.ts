import type { Failure } from "../../../../core/domain/result";
import type { AttendanceExceptionReport } from "../../domain/entities/attendance-exception-report";

export type AttendanceReportState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: AttendanceExceptionReport | null;
  failure: Failure | null;
}>;

export const initialAttendanceReportState: AttendanceReportState = {
  stage: "idle",
  report: null,
  failure: null,
};
