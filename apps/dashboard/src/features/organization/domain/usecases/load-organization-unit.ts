import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUnitId } from "../entities/organization-unit";
import type { OrganizationUnitDetails } from "../entities/organization-unit-details";
import { canReadOrganization, isOrganizationUnitId } from "../policies/organization-unit-policy";
import type { OrganizationRepository } from "../repositories/organization-repository";

export class LoadOrganizationUnit {
  constructor(private readonly units: OrganizationRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<OrganizationUnitDetails>> {
    signal.throwIfAborted();
    if (!canReadOrganization(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isOrganizationUnitId(id)) return Promise.resolve(failed("organization_unit_not_found"));
    return this.units.get(access.companyId, id.toLowerCase() as OrganizationUnitId, signal);
  }
}
