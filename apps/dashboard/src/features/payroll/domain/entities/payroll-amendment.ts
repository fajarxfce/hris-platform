import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CompensationEmployee } from "./compensation-employee";
import type { CompensationTerms } from "./compensation-terms";
import type { PayrollAmendmentCalculationSummary } from "./payroll-amendment-calculation";
import type { PayrollInputTerms } from "./payroll-input-terms";

export const payrollAmendmentStatuses = [
  "DRAFT",
  "CALCULATED",
  "IN_REVIEW",
  "APPROVED",
  "PUBLISHING",
  "PUBLISHED",
  "REJECTED",
  "CANCELLED",
] as const;
export const payrollAmendmentActions = [
  "SAVED",
  "CALCULATED",
  "CALCULATION_FAILED",
  "SUBMITTED",
  "APPROVED",
  "REJECTED",
  "WITHDRAWN",
  "CANCELLED",
  "PUBLICATION_STARTED",
  "PUBLISHED",
  "PUBLICATION_STOPPED",
] as const;
export type PayrollAmendmentStatus = (typeof payrollAmendmentStatuses)[number];
export type PayrollAmendmentSource = Readonly<{ assessmentId: string; revision: number }>;
export type PayrollAmendmentCorrection = Readonly<{
  assessmentId: string;
  compensation: CompensationTerms | null;
  input: PayrollInputTerms | null;
}>;
export type PayrollAmendmentTerms = Readonly<{
  sourceVersions: readonly PayrollAmendmentSource[];
  corrections: readonly PayrollAmendmentCorrection[];
}>;
export type PayrollAmendmentSummary = Readonly<{
  companyId: CompanyId;
  id: string;
  firstAssessmentId: string;
  firstMonth: string;
  employee: CompensationEmployee;
  version: number;
  status: PayrollAmendmentStatus;
  action: (typeof payrollAmendmentActions)[number];
  latestCalculationId: string | null;
  latestReviewId: string | null;
  latestPublicationId: string | null;
  createdAt: string;
  actorId: string;
  recordedAt: string;
  reason: string;
}>;
export type PayrollAmendment = Readonly<{
  summary: PayrollAmendmentSummary;
  terms: PayrollAmendmentTerms;
}>;
export type PayrollAmendmentPage = Readonly<{
  items: readonly PayrollAmendmentSummary[];
  nextCursor: string | null;
}>;
export type PayrollAmendmentReviewSummary = Readonly<{
  id: string;
  amendmentId: string;
  number: number;
  amendmentVersion: number;
  calculationId: string;
  approvalId: string;
  referenceDate: string;
  timezone: string;
  exposure: string;
  currency: "IDR";
  submittedBy: string;
  submittedAt: string;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  version: number;
}>;
export type PayrollAmendmentDetails = Readonly<{
  amendment: PayrollAmendment;
  history: PayrollAmendmentPage;
  latestCalculation: PayrollAmendmentCalculationSummary | null;
  latestReview: PayrollAmendmentReviewSummary | null;
  availableActions: readonly ("EDIT" | "CALCULATE" | "CANCEL" | "SUBMIT")[];
}>;
