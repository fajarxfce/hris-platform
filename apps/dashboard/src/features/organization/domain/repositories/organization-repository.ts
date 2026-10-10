import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { OrganizationUnitId } from "../entities/organization-unit";
import type { OrganizationUnitChange } from "../entities/organization-unit-change";
import type { OrganizationUnitDetails } from "../entities/organization-unit-details";
import type { OrganizationUnitPage } from "../entities/organization-unit-page";
import type { OrganizationUnitSearch } from "../entities/organization-unit-search";

export interface OrganizationRepository {
  save(
    company: CompanyId,
    operation: OperationId,
    change: OrganizationUnitChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    company: CompanyId,
    search: OrganizationUnitSearch,
    signal: AbortSignal,
  ): Promise<Result<OrganizationUnitPage>>;
  get(
    company: CompanyId,
    id: OrganizationUnitId,
    signal: AbortSignal,
  ): Promise<Result<OrganizationUnitDetails>>;
}
