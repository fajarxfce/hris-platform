import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpenseDraftEditReview } from "../../domain/entities/expense-draft-edit-review";
import type { ExpenseDraftEmployee } from "../../domain/entities/expense-draft-employee";
import type { ExpenseDraftReferences } from "../../domain/entities/expense-draft-references";
import type { ExpenseLine } from "../../domain/entities/expense-line";
export type ExpenseDraftEditorState = Readonly<{
  stage:
    | "loading"
    | "choosing"
    | "editing"
    | "saving"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  original: ExpenseDraftEditReview | null;
  employee: Pick<ExpenseDraftEmployee, "id" | "employeeNumber" | "legalName"> | null;
  companyDate: string | null;
  lines: readonly ExpenseLine[];
  references: ExpenseDraftReferences | null;
  changed: boolean;
  panel:
    | Readonly<{ kind: "closed" }>
    | Readonly<{ kind: "line"; line: ExpenseLine; creating: boolean; dirty: boolean }>;
  failure: Failure | null;
  entryFailure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialExpenseDraftEditorState: ExpenseDraftEditorState = Object.freeze({
  stage: "loading",
  original: null,
  employee: null,
  companyDate: null,
  lines: [],
  references: null,
  changed: false,
  panel: { kind: "closed" as const },
  failure: null,
  entryFailure: null,
  receipt: null,
  operationId: null,
});
