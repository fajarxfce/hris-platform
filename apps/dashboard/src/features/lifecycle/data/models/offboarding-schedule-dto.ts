import { z } from "zod";
import { lifecycleCaseDto } from "./lifecycle-case-dto";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true });
const code = z.string().regex(/^[a-z][a-z0-9_.]{0,79}$/u);
export const offboardingScheduleDto = z.object({
  id: z.uuid(),
  caseId: z.uuid(),
  employmentId: z.uuid(),
  status: z.enum(["SCHEDULED", "COMPLETED", "FAILED", "CANCELLED"]),
  version: z.number().int().min(0).max(1),
  caseVersion: version,
  employmentVersion: version,
  appliedEmploymentRevision: version,
  lastWorkingDate: z.string().length(10),
  companyTimezone: z.string().min(1).max(80),
  scheduledFor: instant,
  validUntil: instant,
  attempt: z.number().int().min(1).max(8),
  createdBy: z.uuid(),
  createdAt: instant,
  reason: z.string().min(1).max(1000),
  finishedAt: instant.nullable(),
  failureCode: code.nullable(),
  job: z.object({
    id: z.uuid(),
    status: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
    version,
    attempts: z.number().int().min(0).max(8),
    cancellationRequested: z.boolean(),
    completedItems: z.number().int().min(0).max(1),
    failureCode: code.nullable(),
    finishedAt: instant.nullable(),
  }),
  actions: z.array(z.enum(["cancel", "recover"])).max(1),
});
export const offboardingSchedulesDto = z.object({
  items: z.array(offboardingScheduleDto).max(8),
  nextCursor: z.null(),
});
export const offboardingScheduleReviewDto = z.object({
  case: lifecycleCaseDto,
  employmentVersion: version,
  appliedEmploymentRevision: version,
  companyTimezone: z.string().min(1).max(80),
  today: z.string().length(10),
  scheduledFor: instant,
  validUntil: instant,
  basisRevision: z.string().regex(/^[a-f0-9]{64}$/u),
  attempts: z.number().int().min(0).max(8),
  blockers: z.array(code).max(20),
});
export const offboardingScheduleReceiptDto = z.object({ id: z.uuid(), version });
export type OffboardingScheduleDto = z.infer<typeof offboardingScheduleDto>;
export type OffboardingSchedulesDto = z.infer<typeof offboardingSchedulesDto>;
export type OffboardingScheduleReviewDto = z.infer<typeof offboardingScheduleReviewDto>;
export type OffboardingScheduleReceiptDto = z.infer<typeof offboardingScheduleReceiptDto>;
export type OffboardingScheduleCreationDto = Readonly<{
  id: string;
  expectedVersion: number;
  employmentVersion: number;
  basisRevision: string;
  reason: string;
}>;
export type OffboardingScheduleChangeDto = Readonly<{
  expectedVersion: number;
  expectedJobVersion: number;
  reason: string;
}>;
