import type { Failure } from "../../../../core/domain/result";
import type { OvertimeReport } from "../../domain/entities/overtime-report";

export type OvertimeReportState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  report: OvertimeReport | null;
  failure: Failure | null;
}>;

export const initialOvertimeReportState: OvertimeReportState = {
  stage: "idle",
  report: null,
  failure: null,
};
