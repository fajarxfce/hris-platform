import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleEmployeeReference } from "./lifecycle-employee-reference";
import type { LifecycleTemplate } from "./lifecycle-template";
export type OnboardingScheduleReview = Readonly<{
  companyId: CompanyId;
  employee: LifecycleEmployeeReference;
  employmentVersion: number;
  appliedEmploymentRevision: number;
  template: LifecycleTemplate;
  targetDate: string;
  startsOn: string;
  companyTimezone: string;
  today: string;
  scheduledFor: string;
  validUntil: string;
  basisRevision: string;
  attempts: number;
  blockers: readonly string[];
}>;
