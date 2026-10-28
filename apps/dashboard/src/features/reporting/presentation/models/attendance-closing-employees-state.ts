import type { Failure } from "../../../../core/domain/result";
import type { AttendanceClosingEmployeePage } from "../../domain/entities/attendance-closing-report";

export type AttendanceClosingEmployeesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: AttendanceClosingEmployeePage | null;
  failure: Failure | null;
}>;
export const initialAttendanceClosingEmployeesState: AttendanceClosingEmployeesState = {
  stage: "loading",
  page: null,
  failure: null,
};
