import type { Failure } from "../../../../core/domain/result";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";

export type LeaveRequestState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  request: LeaveRequestDetails | null;
  failure: Failure | null;
}>;
export const initialLeaveRequestState: LeaveRequestState = {
  stage: "loading",
  request: null,
  failure: null,
};
