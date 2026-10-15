import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentPublicationDetails } from "../../domain/entities/payroll-amendment-publication-details";
import type { PayrollAmendmentPublicationPreparation } from "../../domain/entities/payroll-amendment-publication-preparation";
import type { PayrollCommandState } from "./payroll-command-state";

export type PayrollAmendmentPublicationStartState =
  PayrollCommandState<PayrollAmendmentPublicationPreparation> &
    Readonly<{ publicationId: string | null }>;
export type PayrollAmendmentPublicationState = Readonly<{
  stage: "loading" | "updating" | "ready" | "unavailable";
  details: PayrollAmendmentPublicationDetails | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialAmendmentPublicationState: PayrollAmendmentPublicationState = {
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
};
