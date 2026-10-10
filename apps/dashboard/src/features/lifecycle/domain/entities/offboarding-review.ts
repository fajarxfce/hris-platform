import type { LifecycleCase } from "./lifecycle-case";

export type OffboardingReview = Readonly<{
  case: LifecycleCase;
  employmentVersion: number;
  today: string;
}>;
