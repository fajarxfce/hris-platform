import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { ClientPolicyRevision } from "../../domain/entities/client-policy-revision";
import type { MaintenanceWindow } from "../../domain/entities/maintenance-window";
import type { ClientPolicyRevisionDto } from "../models/client-policy-revision-dto";
import type { MaintenanceWindowDto } from "../models/client-policy-values-dto";

export function toMaintenanceWindow(window: MaintenanceWindowDto | null): MaintenanceWindow | null {
  if (window === null) return null;
  const start = utcInstantMicroseconds(window.startsAt);
  const end = utcInstantMicroseconds(window.endsAt);
  if (start === null || end === null || start >= end || end - start > 7n * 86_400_000_000n)
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...window });
}

export function toClientPolicyRevision(
  dto: ClientPolicyRevisionDto,
  companyId: CompanyId,
  version?: number,
): ClientPolicyRevision {
  if (
    (version !== undefined && dto.version !== version) ||
    new Set(dto.disabledModules).size !== dto.disabledModules.length ||
    utcInstantMicroseconds(dto.activateAt) === null ||
    utcInstantMicroseconds(dto.recordedAt) === null
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    companyId,
    actorId: dto.actorId as AccountId,
    disabledModules: Object.freeze([...dto.disabledModules]),
    minimumBuilds: Object.freeze({ ...dto.minimumBuilds }),
    maintenance: toMaintenanceWindow(dto.maintenance),
  });
}
