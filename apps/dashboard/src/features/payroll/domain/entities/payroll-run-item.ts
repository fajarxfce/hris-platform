import type { Failure } from "../../../../core/domain/result";
export type PayrollRunTarget = Readonly<{
  ordinal: number;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  employmentVersion: number;
  compensationRevision: number | null;
  inputId: string | null;
  inputRevision: number | null;
  taxOpeningId: string | null;
  taxOpeningRevision: number | null;
  previousAssessmentId: string | null;
}>;
export type PayrollRunItem = Readonly<{
  target: PayrollRunTarget;
  jobId: string;
  completedAt: string;
  failure: (Failure & Readonly<{ kind: string }>) | null;
  taxableGross: string | null;
  withheld: string | null;
  takeHome: string | null;
}>;
