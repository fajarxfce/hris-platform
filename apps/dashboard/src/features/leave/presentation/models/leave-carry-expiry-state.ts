import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeaveCarryExpiryPage } from "../../domain/entities/leave-carry-expiry";
import type { LeaveCarryExpiryDetails } from "../../domain/entities/leave-carry-expiry-details";
export type LeaveCarryExpiriesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: LeaveCarryExpiryPage | null;
  failure: Failure | null;
}>;
export const initialLeaveCarryExpiriesState: LeaveCarryExpiriesState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
export type LeaveCarryExpiryState = Readonly<{
  stage: "loading" | "ready" | "updating" | "unavailable";
  details: LeaveCarryExpiryDetails | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialLeaveCarryExpiryState: LeaveCarryExpiryState = Object.freeze({
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
});
export type LeaveCarryExpiryCommandState = Readonly<{
  stage: "loading" | "unavailable" | "reviewing" | "saving" | "unconfirmed" | "conflict" | "saved";
  review: LeaveCarryExpiryDetails | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
  expiryId: string | null;
}>;
export const initialLeaveCarryExpiryCommand: LeaveCarryExpiryCommandState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
  expiryId: null,
});
