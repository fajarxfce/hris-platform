import { z } from "zod";
import { lifecycleCaseDto } from "./lifecycle-case-dto";
import { lifecycleScheduleJobDto } from "./lifecycle-schedule-job-dto";
import { lifecycleTemplateDto } from "./lifecycle-template-dto";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true });
const code = z.string().regex(/^[a-z][a-z0-9_.]{0,79}$/u);
export const onboardingScheduleDto = z.object({
  id: z.uuid(),
  caseId: z.uuid(),
  employmentId: z.uuid(),
  status: z.enum(["SCHEDULED", "STARTED", "FAILED", "CANCELLED"]),
  version: z.number().int().min(0).max(1),
  employmentVersion: version,
  appliedEmploymentRevision: version,
  template: lifecycleTemplateDto,
  assignees: z
    .record(z.string().regex(/^[a-z][a-z0-9_-]{0,47}$/u), z.uuid())
    .refine((value) => Object.keys(value).length <= 64),
  targetDate: z.string().length(10),
  startsOn: z.string().length(10),
  companyTimezone: z.string().min(1).max(80),
  scheduledFor: instant,
  validUntil: instant,
  attempt: z.number().int().min(1).max(8),
  createdBy: z.uuid(),
  createdAt: instant,
  reason: z.string().min(1).max(1000),
  finishedAt: instant.nullable(),
  failureCode: code.nullable(),
  job: lifecycleScheduleJobDto,
  actions: z.array(z.enum(["cancel", "recover"])).max(1),
});
export const onboardingSchedulesDto = z.object({
  items: z.array(onboardingScheduleDto).max(8),
  nextCursor: z.null(),
});
export const onboardingScheduleReviewDto = z.object({
  employee: lifecycleCaseDto.shape.employee,
  employmentVersion: version,
  appliedEmploymentRevision: version,
  template: lifecycleTemplateDto,
  targetDate: z.string().length(10),
  startsOn: z.string().length(10),
  companyTimezone: z.string().min(1).max(80),
  today: z.string().length(10),
  scheduledFor: instant,
  validUntil: instant,
  basisRevision: z.string().regex(/^[a-f0-9]{64}$/u),
  attempts: z.number().int().min(0).max(8),
  blockers: z.array(code).max(20),
});
export const onboardingScheduleReceiptDto = z.object({ id: z.uuid(), version });
export type OnboardingScheduleDto = z.infer<typeof onboardingScheduleDto>;
export type OnboardingSchedulesDto = z.infer<typeof onboardingSchedulesDto>;
export type OnboardingScheduleReviewDto = z.infer<typeof onboardingScheduleReviewDto>;
export type OnboardingScheduleReceiptDto = z.infer<typeof onboardingScheduleReceiptDto>;
export type OnboardingScheduleSelectionDto = Readonly<{
  templateId: string;
  targetDate: string;
  startsOn: string;
}>;
export type OnboardingScheduleCreationDto = OnboardingScheduleSelectionDto &
  Readonly<{
    id: string;
    caseId: string;
    employmentVersion: number;
    templateVersion: number;
    basisRevision: string;
    assignees: Readonly<Record<string, string>>;
    reason: string;
  }>;
export type OnboardingScheduleChangeDto = Readonly<{
  expectedVersion: number;
  expectedJobVersion: number;
  reason: string;
}>;
