import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const employee = z.object({
  id: z.uuid(),
  employeeNumber: z.string().min(2).max(32),
  name: z.string().min(1).max(200),
});
export const lifecycleTaskDto = z.object({
  key: z.string().min(1).max(48),
  title: z.string().min(1).max(160),
  required: z.boolean(),
  dueDate: z.string().length(10),
  assigneeId: z.uuid().nullable(),
  status: z.enum(["PENDING", "DONE", "WAIVED"]),
  completedBy: z.uuid().nullable(),
  completedAt: z.string().min(1).max(40).nullable(),
});
export type LifecycleTaskDto = z.infer<typeof lifecycleTaskDto>;
export const lifecycleCaseDto = z.object({
  id: z.uuid(),
  employmentId: z.uuid(),
  employee,
  kind: z.enum(["ONBOARDING", "OFFBOARDING"]),
  targetDate: z.string().length(10),
  templateId: z.uuid(),
  templateVersion: version,
  templateName: z.string().min(1).max(120),
  status: z.enum(["OPEN", "COMPLETED", "CANCELLED"]),
  version,
  createdBy: z.uuid(),
  createdAt: z.string().min(1).max(40),
  tasks: z.array(lifecycleTaskDto).min(1).max(64),
});
export type LifecycleCaseDto = z.infer<typeof lifecycleCaseDto>;
export const lifecycleCasePageDto = z.object({
  items: z.array(lifecycleCaseDto).max(10),
  nextCursor: z.uuid().nullable(),
});
export type LifecycleCasePageDto = z.infer<typeof lifecycleCasePageDto>;
export type LifecycleCaseQuery = Readonly<{
  status: string | null;
  employmentId: string | null;
  after: string | null;
}>;
export const assignedLifecycleTaskDto = z.object({
  caseId: z.uuid(),
  employmentId: z.uuid(),
  employee,
  kind: z.enum(["ONBOARDING", "OFFBOARDING"]),
  caseVersion: version,
  task: lifecycleTaskDto,
});
export type AssignedLifecycleTaskDto = z.infer<typeof assignedLifecycleTaskDto>;
export const assignedLifecycleTaskPageDto = z.object({
  items: z.array(assignedLifecycleTaskDto).max(50),
  nextCursor: z.string().min(38).max(85).nullable(),
});
export type AssignedLifecycleTaskPageDto = z.infer<typeof assignedLifecycleTaskPageDto>;
