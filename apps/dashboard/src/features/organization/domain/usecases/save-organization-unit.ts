import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUnitChange } from "../entities/organization-unit-change";
import {
  canManageOrganization,
  normalizeOrganizationChange,
} from "../policies/organization-change-policy";
import { isOrganizationUnitId } from "../policies/organization-unit-policy";
import type { OrganizationRepository } from "../repositories/organization-repository";

export class SaveOrganizationUnit {
  constructor(private readonly units: OrganizationRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: OrganizationUnitChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageOrganization(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isOrganizationUnitId(operation))
      return Promise.resolve(failed("invalid_organization_unit"));
    const change = normalizeOrganizationChange(input);
    if (!change.ok) return Promise.resolve(change);
    return this.units.save(access.companyId, operation, change.value, signal);
  }
}
