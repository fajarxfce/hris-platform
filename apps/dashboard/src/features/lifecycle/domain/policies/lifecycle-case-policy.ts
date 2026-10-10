import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import {
  type LifecycleCaseId,
  type LifecycleCaseStatus,
  lifecycleCaseStatuses,
} from "../entities/lifecycle-case";
import type { LifecycleCaseFilter, LifecycleCaseSearch } from "../entities/lifecycle-case-search";
import { isLifecycleTaskKey } from "./lifecycle-template-policy";

export const canReadAssignedLifecycle = (permissions: readonly string[]): boolean =>
  permissions.includes("people.lifecycle.perform") ||
  permissions.includes("people.lifecycle.manage");

export function normalizeLifecycleCaseSearch(
  search: LifecycleCaseSearch,
): Result<LifecycleCaseFilter> {
  if (
    (search.status !== "" && !lifecycleCaseStatuses.some((status) => status === search.status)) ||
    (search.employmentId !== null && !isUuid(search.employmentId)) ||
    (search.after !== null && !isUuid(search.after))
  )
    return failed("invalid_page");
  return success(
    Object.freeze({
      status: search.status === "" ? null : (search.status as LifecycleCaseStatus),
      employmentId: (search.employmentId?.toLowerCase() as EmployeeId) ?? null,
      after: (search.after?.toLowerCase() as LifecycleCaseId) ?? null,
    }),
  );
}

export function isAssignedLifecycleCursor(after: string): boolean {
  if (after.length < 38 || after.length > 85) return false;
  const parts = after.split(":");
  return parts.length === 2 && isUuid(parts[0] ?? "") && isLifecycleTaskKey(parts[1] ?? "");
}

export const isLifecycleHistoryCursor = (after: string): boolean =>
  /^(0|[1-9]\d{0,15})$/u.test(after) && Number.isSafeInteger(Number(after));
