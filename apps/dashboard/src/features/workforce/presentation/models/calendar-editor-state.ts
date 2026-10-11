import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { CalendarReview } from "../../domain/entities/calendar-review";

export type CalendarEditorState = Readonly<{
  stage: "loading" | "editing" | "conflict" | "saving" | "unconfirmed" | "saved" | "unavailable";
  review: CalendarReview | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialCalendarEditorState: CalendarEditorState = {
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
};
