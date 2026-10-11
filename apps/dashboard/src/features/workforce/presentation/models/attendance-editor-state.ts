import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { AttendanceDay } from "../../domain/entities/attendance-day";
import type { AttendanceEntry } from "../../domain/entities/attendance-entry";
import type { WorkforceEmployee } from "../../domain/entities/workforce-employee";

export type AttendanceEditorMode = Readonly<
  { kind: "review"; entryId: string } | { kind: "correction" }
>;
export type AttendanceEditorState = Readonly<{
  stage: "loading" | "editing" | "conflict" | "saving" | "unconfirmed" | "saved" | "unavailable";
  employee: WorkforceEmployee | null;
  day: AttendanceDay | null;
  entry: AttendanceEntry | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialAttendanceEditorState: AttendanceEditorState = {
  stage: "loading",
  employee: null,
  day: null,
  entry: null,
  failure: null,
  receipt: null,
  operationId: null,
};
