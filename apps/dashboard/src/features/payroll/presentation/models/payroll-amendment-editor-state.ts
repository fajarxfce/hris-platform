import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentTerms } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentPreview } from "../../domain/entities/payroll-amendment-calculation";
import type {
  PayrollAmendmentCandidates,
  PayrollAmendmentEditorContext,
} from "../../domain/entities/payroll-amendment-preparation";
export type PayrollAmendmentSourcesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: PayrollAmendmentCandidates | null;
  failure: Failure | null;
}>;
export const initialPayrollAmendmentSourcesState: PayrollAmendmentSourcesState = {
  stage: "loading",
  data: null,
  failure: null,
};
export type PayrollAmendmentEditorState = Readonly<{
  stage:
    | "loading"
    | "editing"
    | "source-review"
    | "previewing"
    | "conflict"
    | "saving"
    | "unconfirmed"
    | "saved"
    | "unavailable";
  context: PayrollAmendmentEditorContext | null;
  terms: PayrollAmendmentTerms | null;
  preview: PayrollAmendmentPreview | null;
  selectedAssessment: string | null;
  dirty: boolean;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialPayrollAmendmentEditorState: PayrollAmendmentEditorState = {
  stage: "loading",
  context: null,
  terms: null,
  preview: null,
  selectedAssessment: null,
  dirty: false,
  failure: null,
  receipt: null,
  operationId: null,
};
