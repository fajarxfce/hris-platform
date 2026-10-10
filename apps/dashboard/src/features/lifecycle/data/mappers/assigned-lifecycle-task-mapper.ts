import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type {
  AssignedLifecycleTaskPage,
  LifecycleTaskContext,
} from "../../domain/entities/lifecycle-task-context";
import { isAssignedLifecycleCursor } from "../../domain/policies/lifecycle-case-policy";
import type { AssignedLifecycleTaskPageDto } from "../models/lifecycle-case-dto";
import { toLifecycleEmployee, toLifecycleTask } from "./lifecycle-case-mapper";

export function toAssignedLifecycleTaskPage(
  dto: AssignedLifecycleTaskPageDto,
  companyId: CompanyId,
  account: AccountId,
  after: string | null,
): AssignedLifecycleTaskPage {
  const items: LifecycleTaskContext[] = dto.items.map((item) =>
    Object.freeze({
      companyId,
      caseId: item.caseId.toLowerCase() as LifecycleCaseId,
      caseVersion: item.caseVersion,
      caseStatus: "OPEN" as const,
      employee: toLifecycleEmployee(item.employee, item.employmentId),
      kind: item.kind,
      task: toLifecycleTask(item.task),
    }),
  );
  const keys = items.map((item) => `${item.caseId}:${item.task.key}`);
  const nextCursor =
    dto.nextCursor === null
      ? null
      : `${dto.nextCursor.slice(0, 36).toLowerCase()}${dto.nextCursor.slice(36)}`;
  const cases = new Map<string, LifecycleTaskContext>();
  for (const item of items) {
    const previous = cases.get(item.caseId);
    if (
      previous &&
      (previous.caseVersion !== item.caseVersion ||
        previous.kind !== item.kind ||
        previous.employee.id !== item.employee.id ||
        previous.employee.name !== item.employee.name ||
        previous.employee.employeeNumber !== item.employee.employeeNumber)
    )
      throw new InvalidHttpResponseError();
    cases.set(item.caseId, item);
  }
  if (
    new Set(keys).size !== keys.length ||
    keys.includes(after ?? "") ||
    items.some(
      (item, index) =>
        item.task.assigneeId !== account ||
        item.task.status !== "PENDING" ||
        (after !== null && item.caseId < after.slice(0, 36)) ||
        (index > 0 && item.caseId < (items[index - 1]?.caseId ?? "")),
    ) ||
    (nextCursor !== null &&
      (!isAssignedLifecycleCursor(nextCursor) || items.length !== 50 || nextCursor !== keys.at(-1)))
  )
    throw new InvalidHttpResponseError();
  // Within a case, task-key ordering follows the database collation.
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
