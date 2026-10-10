import type { Failure } from "../../../../core/domain/result";

export type LeaveAttachmentsState = Readonly<{
  stage: "idle" | "downloading" | "started" | "failed" | "unavailable";
  revisionId: string | null;
  failure: Failure | null;
}>;
export const initialLeaveAttachmentsState: LeaveAttachmentsState = Object.freeze({
  stage: "idle",
  revisionId: null,
  failure: null,
});
