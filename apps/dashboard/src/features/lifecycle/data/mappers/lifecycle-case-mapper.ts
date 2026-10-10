import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type {
  LifecycleCase,
  LifecycleCaseId,
  LifecycleCasePage,
} from "../../domain/entities/lifecycle-case";
import type { LifecycleCaseFilter } from "../../domain/entities/lifecycle-case-search";
import type { LifecycleEmployeeReference } from "../../domain/entities/lifecycle-employee-reference";
import type { LifecycleTask } from "../../domain/entities/lifecycle-task";
import type { LifecycleTemplateId } from "../../domain/entities/lifecycle-template";
import { isLifecycleTaskKey } from "../../domain/policies/lifecycle-template-policy";
import type {
  LifecycleCaseDto,
  LifecycleCasePageDto,
  LifecycleTaskDto,
} from "../models/lifecycle-case-dto";

export function toLifecycleEmployee(
  dto: LifecycleCaseDto["employee"],
  employmentId: string,
): LifecycleEmployeeReference {
  if (
    dto.id.toLowerCase() !== employmentId.toLowerCase() ||
    !dto.name.trim() ||
    !dto.employeeNumber.trim()
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as EmployeeId,
    employeeNumber: dto.employeeNumber,
    name: dto.name,
  });
}
export function toLifecycleTask(dto: LifecycleTaskDto): LifecycleTask {
  if (
    !isLifecycleTaskKey(dto.key) ||
    !dto.title.trim() ||
    !isCalendarDate(dto.dueDate) ||
    (dto.status === "PENDING"
      ? dto.completedAt !== null || dto.completedBy !== null
      : dto.completedAt === null || dto.completedBy === null) ||
    (dto.status === "WAIVED" && dto.required) ||
    (dto.completedAt !== null && utcInstantMicroseconds(dto.completedAt) === null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    key: dto.key,
    title: dto.title,
    required: dto.required,
    dueDate: dto.dueDate,
    assigneeId: dto.assigneeId === null ? null : (dto.assigneeId.toLowerCase() as AccountId),
    status: dto.status,
    completedBy: dto.completedBy === null ? null : (dto.completedBy.toLowerCase() as AccountId),
    completedAt: dto.completedAt,
  });
}
export function toLifecycleCase(dto: LifecycleCaseDto, companyId: CompanyId): LifecycleCase {
  if (
    !isCalendarDate(dto.targetDate) ||
    !dto.templateName.trim() ||
    utcInstantMicroseconds(dto.createdAt) === null ||
    new Set(dto.tasks.map((task) => task.key)).size !== dto.tasks.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as LifecycleCaseId,
    companyId,
    employee: toLifecycleEmployee(dto.employee, dto.employmentId),
    kind: dto.kind,
    targetDate: dto.targetDate,
    templateId: dto.templateId.toLowerCase() as LifecycleTemplateId,
    templateVersion: dto.templateVersion,
    templateName: dto.templateName,
    status: dto.status,
    version: dto.version,
    createdBy: dto.createdBy.toLowerCase() as AccountId,
    createdAt: dto.createdAt,
    tasks: Object.freeze(dto.tasks.map(toLifecycleTask)),
  });
}
export function toLifecycleCaseDetails(
  dto: LifecycleCaseDto,
  companyId: CompanyId,
  id: LifecycleCaseId,
): LifecycleCase {
  const value = toLifecycleCase(dto, companyId);
  if (value.id !== id) throw new InvalidHttpResponseError();
  return value;
}
export function toLifecycleCasePage(
  dto: LifecycleCasePageDto,
  companyId: CompanyId,
  filter: LifecycleCaseFilter,
): LifecycleCasePage {
  const items = dto.items.map((item) => toLifecycleCase(item, companyId));
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        (filter.status !== null && item.status !== filter.status) ||
        (filter.employmentId !== null && item.employee.id !== filter.employmentId) ||
        (filter.after !== null && item.id <= filter.after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 10 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
