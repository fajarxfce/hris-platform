import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { SavedReportFilter } from "../../domain/entities/saved-report-filter";
export type ReportFilterEditorState = Readonly<{
  stage:
    | "loading"
    | "unavailable"
    | "editing"
    | "confirming-delete"
    | "saving"
    | "unconfirmed"
    | "conflict"
    | "completed";
  filter: SavedReportFilter | null;
  command: "save" | "delete" | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
  failure: Failure | null;
}>;
export const initialReportFilterEditorState: ReportFilterEditorState = {
  stage: "loading",
  filter: null,
  command: null,
  receipt: null,
  operationId: null,
  failure: null,
};
