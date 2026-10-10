import type { Failure } from "../../../../core/domain/result";
import type { LeaveRequestPage } from "../../domain/entities/leave-request";

export type LeaveRequestsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LeaveRequestPage | null;
  failure: Failure | null;
}>;
export const initialLeaveRequestsState: LeaveRequestsState = {
  stage: "loading",
  page: null,
  failure: null,
};
