import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { lifecycleKinds } from "../entities/lifecycle-template";
import type { LifecycleTemplateChange } from "../entities/lifecycle-template-change";
import { isLifecycleTaskKey, isLifecycleTemplateCode } from "./lifecycle-template-policy";

export function normalizeLifecycleTemplateChange(
  input: LifecycleTemplateChange,
): Result<LifecycleTemplateChange> {
  if (input.tasks.length < 1 || input.tasks.length > 64)
    return failed("invalid_lifecycle_template");
  const change = Object.freeze({
    id: input.id.toLowerCase(),
    expectedVersion: input.expectedVersion,
    code: input.code.trim().toUpperCase(),
    name: input.name.trim(),
    kind: input.kind,
    active: input.active,
    tasks: Object.freeze(
      input.tasks.map((task) =>
        Object.freeze({
          key: task.key.trim(),
          title: task.title.trim(),
          required: task.required,
          dueDays: task.dueDays,
        }),
      ),
    ),
    reason: input.reason.trim(),
  });
  if (
    !isUuid(change.id) ||
    !isLifecycleTemplateCode(change.code) ||
    change.name.length === 0 ||
    change.name.length > 120 ||
    !lifecycleKinds.includes(change.kind) ||
    (change.expectedVersion !== null &&
      (!Number.isSafeInteger(change.expectedVersion) ||
        change.expectedVersion < 0 ||
        change.expectedVersion === Number.MAX_SAFE_INTEGER)) ||
    new Set(change.tasks.map((task) => task.key)).size !== change.tasks.length ||
    change.tasks.some(
      (task) =>
        !isLifecycleTaskKey(task.key) ||
        task.title.length === 0 ||
        task.title.length > 160 ||
        !Number.isInteger(task.dueDays) ||
        task.dueDays < -90 ||
        task.dueDays > 365,
    ) ||
    change.reason.length === 0 ||
    change.reason.length > 1000
  )
    return failed("invalid_lifecycle_template");
  return success(change);
}

/** A rejected retry cannot resolve an earlier command whose response was lost. */
export function lifecycleTemplateSaveWasRejected(failure: Failure): boolean {
  return [
    "invalid_lifecycle_template",
    "lifecycle_template_identity_immutable",
    "lifecycle_template_exists",
    "lifecycle_template_not_found",
    "lifecycle_template_limit",
    "stale_version",
    "data_conflict",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
}
