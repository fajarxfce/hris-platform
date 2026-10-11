import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { WorkHoliday } from "../../domain/entities/work-holiday";

export type HolidayEditorState = Readonly<{
  stage: "loading" | "editing" | "conflict" | "saving" | "unconfirmed" | "saved" | "unavailable";
  holiday: WorkHoliday | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialHolidayEditorState: HolidayEditorState = {
  stage: "loading",
  holiday: null,
  failure: null,
  receipt: null,
  operationId: null,
};
