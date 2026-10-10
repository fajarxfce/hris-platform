import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUnitPage } from "../entities/organization-unit-page";
import type { OrganizationUnitSearchInput } from "../entities/organization-unit-search";
import {
  canReadOrganization,
  parseOrganizationUnitSearch,
} from "../policies/organization-unit-policy";
import type { OrganizationRepository } from "../repositories/organization-repository";

export class LoadOrganizationUnits {
  constructor(private readonly units: OrganizationRepository) {}
  execute(
    access: CompanyAccess,
    input: OrganizationUnitSearchInput,
    signal: AbortSignal,
  ): Promise<Result<OrganizationUnitPage>> {
    signal.throwIfAborted();
    if (!canReadOrganization(access.permissions)) return Promise.resolve(failed("access_denied"));
    const search = parseOrganizationUnitSearch(input);
    if (!search.ok) return Promise.resolve(search);
    return this.units.list(access.companyId, search.value, signal);
  }
}
