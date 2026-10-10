import type { OrganizationSearchDto } from "../models/organization-search-dto";
import type {
  OrganizationUnitDetailsDto,
  OrganizationUnitPageDto,
} from "../models/organization-unit-dto";

export interface OrganizationDataSource {
  list(
    company: string,
    search: OrganizationSearchDto,
    signal: AbortSignal,
  ): Promise<OrganizationUnitPageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<OrganizationUnitDetailsDto>;
}
