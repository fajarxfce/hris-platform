import type { LifecycleCase } from "./lifecycle-case";
export type OffboardingScheduleReview = Readonly<{
  case: LifecycleCase;
  employmentVersion: number;
  appliedEmploymentRevision: number;
  companyTimezone: string;
  today: string;
  scheduledFor: string;
  validUntil: string;
  basisRevision: string;
  attempts: number;
  blockers: readonly string[];
}>;
