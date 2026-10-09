import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { ClientPolicySettings } from "../../domain/entities/client-policy-settings";
import { companyModules } from "../../domain/entities/company-module";
import type { ClientPolicySettingsDto } from "../models/client-policy-settings-dto";
import { toClientPolicyRevision, toMaintenanceWindow } from "./client-policy-revision-mapper";

export function toClientPolicySettings(
  dto: ClientPolicySettingsDto,
  companyId: CompanyId,
): ClientPolicySettings {
  const latest = dto.latest === null ? null : toClientPolicyRevision(dto.latest, companyId);
  const current = dto.effective;
  const at = utcInstantMicroseconds(current.serverTime);
  const until = utcInstantMicroseconds(current.validUntil);
  const latestActivation = latest === null ? null : utcInstantMicroseconds(latest.activateAt);
  const enabled = new Set(current.enabledModules);
  const maintenance = toMaintenanceWindow(current.maintenance);
  if (
    at === null ||
    until === null ||
    until < at ||
    until - at > 60_000_000n ||
    enabled.size !== current.enabledModules.length ||
    (latest === null && current.version !== null) ||
    (current.version !== null && latest !== null && current.version > latest.version) ||
    (latest !== null &&
      latestActivation !== null &&
      latestActivation <= at &&
      current.version !== latest.version)
  )
    throw new InvalidHttpResponseError();
  if (
    current.version === null &&
    (enabled.size !== companyModules.length ||
      current.maintenance !== null ||
      Object.values(current.minimumBuilds).some((value) => value !== 0))
  )
    throw new InvalidHttpResponseError();
  if (
    latest !== null &&
    current.version === latest.version &&
    (latest.disabledModules.some((module) => enabled.has(module)) ||
      enabled.size + latest.disabledModules.length !== companyModules.length ||
      current.minimumBuilds.android !== latest.minimumBuilds.android ||
      current.minimumBuilds.ios !== latest.minimumBuilds.ios ||
      current.minimumBuilds.web !== latest.minimumBuilds.web ||
      (latestActivation !== null && latestActivation > at) ||
      (maintenance === null) !== (latest.maintenance === null) ||
      (maintenance !== null &&
        latest.maintenance !== null &&
        (utcInstantMicroseconds(maintenance.startsAt) !==
          utcInstantMicroseconds(latest.maintenance.startsAt) ||
          utcInstantMicroseconds(maintenance.endsAt) !==
            utcInstantMicroseconds(latest.maintenance.endsAt))))
  )
    throw new InvalidHttpResponseError();
  const startsAt = maintenance === null ? null : utcInstantMicroseconds(maintenance.startsAt);
  const endsAt = maintenance === null ? null : utcInstantMicroseconds(maintenance.endsAt);
  const active = startsAt !== null && endsAt !== null && startsAt <= at && at < endsAt;
  if (active !== current.maintenanceActive) throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId,
    latest,
    effective: Object.freeze({
      version: current.version,
      enabledModules: Object.freeze([...current.enabledModules]),
      minimumBuilds: Object.freeze({ ...current.minimumBuilds }),
      maintenance,
      maintenanceActive: current.maintenanceActive,
      evaluatedAt: current.serverTime,
      validUntil: current.validUntil,
    }),
  });
}
