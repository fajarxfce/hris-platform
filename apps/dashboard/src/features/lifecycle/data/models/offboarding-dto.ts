import { z } from "zod";
import type { LifecycleCaseChangeDto } from "./lifecycle-case-change-dto";
import { lifecycleCaseDto } from "./lifecycle-case-dto";

export const offboardingReviewDto = z.object({
  case: lifecycleCaseDto,
  employmentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  today: z.string().length(10),
});
export type OffboardingReviewDto = z.infer<typeof offboardingReviewDto>;
export type OffboardingCompletionDto = LifecycleCaseChangeDto &
  Readonly<{ employmentVersion: number }>;
