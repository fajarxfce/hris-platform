import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CompensationEmployee } from "./compensation-employee";
import type { CompensationTerms } from "./compensation-terms";
import type {
  PayrollAmendment,
  PayrollAmendmentSource,
  PayrollAmendmentTerms,
} from "./payroll-amendment";
import type { PayrollAmendmentSearch } from "./payroll-amendment-search";
import type { PayrollCalculationOutput } from "./payroll-calculation-output";
import type { PayrollInputTerms } from "./payroll-input-terms";

export type PayrollAmendmentSourceSearch = Omit<PayrollAmendmentSearch, "status">;
export type PayrollAmendmentCandidate = Readonly<{
  companyId: CompanyId;
  assessmentId: string;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  taxMonth: string;
  revision: number;
  publishedAt: string;
}>;
export type PayrollAmendmentCandidates = Readonly<{
  items: readonly PayrollAmendmentCandidate[];
  nextCursor: string | null;
}>;
export type PayrollAmendmentBasis = Readonly<{
  source: PayrollAmendmentSource;
  employee: CompensationEmployee;
  month: string;
  compensation: CompensationTerms;
  input: PayrollInputTerms;
  calculation: PayrollCalculationOutput;
}>;
export type PayrollAmendmentPreparation = Readonly<{
  companyId: CompanyId;
  firstAssessmentId: string;
  sourceVersions: readonly PayrollAmendmentSource[];
  assessments: readonly PayrollAmendmentBasis[];
}>;
export type PayrollAmendmentEditorTarget =
  | Readonly<{ kind: "create"; assessmentId: string }>
  | Readonly<{ kind: "edit"; amendmentId: string }>;
export type PayrollAmendmentEditorContext = Readonly<{
  preparation: PayrollAmendmentPreparation;
  current: PayrollAmendment | null;
  sourcesChanged: boolean;
}>;
export type PayrollAmendmentSave = PayrollAmendmentTerms &
  Readonly<{
    firstAssessmentId: string;
    expectedVersion: number | null;
    reason: string;
  }>;
