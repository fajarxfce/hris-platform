import type { PayrollAmendmentSummary } from "./payroll-amendment";
import type { PayrollAmendmentPublication } from "./payroll-amendment-publication";
import type { PayrollRunJob } from "./payroll-run-details";

export type PayrollAmendmentPublicationDetails = Readonly<{
  publication: PayrollAmendmentPublication;
  job: PayrollRunJob;
  amendment: PayrollAmendmentSummary;
  availableActions: readonly "RECOVER"[];
}>;
