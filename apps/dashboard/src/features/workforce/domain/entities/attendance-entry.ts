import type { ScheduledDay } from "./scheduled-day";

export type AttendanceKind = "CLOCK_IN" | "CLOCK_OUT";
export type AttendanceStatus = "ACCEPTED" | "PENDING" | "REJECTED";
export type AttendanceIssue =
  | "OFFLINE"
  | "PERIOD_LOCKED"
  | "CORRECTED_DAY"
  | "UNVERIFIED_CAPTURE"
  | "WINDOW_EXPIRED"
  | "LOCATION_REQUIRED"
  | "LOW_ACCURACY"
  | "OUTSIDE_FENCE"
  | "MOCK_LOCATION"
  | "UNSCHEDULED"
  | "OUTSIDE_SHIFT_WINDOW"
  | "SEQUENCE_REVIEW";
export type AttendanceEntry = Readonly<{
  id: string;
  kind: AttendanceKind;
  capturedAt: string;
  receivedAt: string;
  offline: boolean;
  location: Readonly<{
    latitude: number;
    longitude: number;
    accuracyMeters: number;
    mocked: boolean;
  }> | null;
  schedule: ScheduledDay;
  status: AttendanceStatus;
  initialStatus: "ACCEPTED" | "PENDING";
  issues: readonly AttendanceIssue[];
  review: Readonly<{
    actorId: string;
    decision: "ACCEPT" | "REJECT";
    reviewedAt: string;
    reason: string;
  }> | null;
  version: number;
}>;
