import type { PayrollAmendmentSummary } from "./payroll-amendment";
import type { PayrollAmendmentPublicationDetails } from "./payroll-amendment-publication-details";

export type PayrollAmendmentPublications = Readonly<{
  amendment: PayrollAmendmentSummary;
  attempts: readonly PayrollAmendmentPublicationDetails[];
  availableActions: readonly "START"[];
}>;
