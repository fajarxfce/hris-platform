import type { LifecycleCaseChange } from "./lifecycle-case-change";

export type OffboardingCompletion = LifecycleCaseChange & Readonly<{ employmentVersion: number }>;
