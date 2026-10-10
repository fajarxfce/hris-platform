import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { ClientPolicyChange } from "../entities/client-policy-change";
import { companyModules } from "../entities/company-module";

/** Clock-dependent activation and admission are evaluated by the server after its command guards. */
export function normalizeClientPolicyChange(input: ClientPolicyChange): Result<ClientPolicyChange> {
  const fields: Record<string, string> = {};
  const reason = input.reason.trim();
  if (
    input.expectedVersion !== null &&
    (!Number.isInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion > 9999)
  )
    fields.expectedVersion = "invalid_revision";
  if (!reason || reason.length > 1000 || /\p{Cc}/u.test(reason)) fields.reason = "invalid_reason";
  if (
    input.disabledModules.length > companyModules.length ||
    new Set(input.disabledModules).size !== input.disabledModules.length ||
    input.disabledModules.some((module) => !companyModules.includes(module))
  )
    fields.disabledModules = "invalid_module";
  for (const platform of ["android", "ios", "web"] as const) {
    const build = input.minimumBuilds[platform];
    if (!Number.isInteger(build) || build < 0 || build > 999_999_999)
      fields[`minimumBuilds.${platform}`] = "invalid_build_number";
  }
  for (const [field, at] of [
    ["activateAt", input.activateAt],
    ["maintenance.startsAt", input.maintenance?.startsAt ?? null],
    ["maintenance.endsAt", input.maintenance?.endsAt ?? null],
  ] as const) {
    if (
      at !== null &&
      (utcInstantMicroseconds(at) === null || at < "2024-01-01" || at >= "2101-01-01")
    )
      fields[field] = "invalid_timestamp";
  }
  if (input.maintenance) {
    const start = utcInstantMicroseconds(input.maintenance.startsAt);
    const end = utcInstantMicroseconds(input.maintenance.endsAt);
    if (start !== null && end !== null && (end <= start || end - start > 604_800_000_000n))
      fields.maintenance = "invalid_maintenance_window";
  }
  if (Object.keys(fields).length)
    return {
      ok: false,
      failure: { code: "invalid_client_policy", fields: Object.freeze(fields), parameters: {} },
    };
  if (input.expectedVersion === 9999) return failed("client_policy_revision_limit");
  return success(
    Object.freeze({
      expectedVersion: input.expectedVersion,
      activateAt: input.activateAt,
      disabledModules: Object.freeze(
        companyModules.filter((module) => input.disabledModules.includes(module)),
      ),
      minimumBuilds: Object.freeze({
        android: input.minimumBuilds.android,
        ios: input.minimumBuilds.ios,
        web: input.minimumBuilds.web,
      }),
      maintenance:
        input.maintenance === null
          ? null
          : Object.freeze({
              startsAt: input.maintenance.startsAt,
              endsAt: input.maintenance.endsAt,
            }),
      reason,
    }),
  );
}

export function clientPolicyChangeWasRejected(failure: Failure): boolean {
  return [
    "invalid_client_policy",
    "client_policy_activation_expired",
    "client_policy_activation_too_late",
    "client_policy_revision_limit",
    "client_policy_clock_regressed",
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
    "request_rate_limited",
  ].includes(failure.code);
}
