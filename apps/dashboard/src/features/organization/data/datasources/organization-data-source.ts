import type {
  OrganizationChangeDto,
  OrganizationReceiptDto,
} from "../models/organization-change-dto";
import type { OrganizationSearchDto } from "../models/organization-search-dto";
import type {
  OrganizationUnitDetailsDto,
  OrganizationUnitPageDto,
} from "../models/organization-unit-dto";

export interface OrganizationDataSource {
  save(
    company: string,
    id: string,
    operation: string,
    change: OrganizationChangeDto,
    signal: AbortSignal,
  ): Promise<OrganizationReceiptDto>;
  list(
    company: string,
    search: OrganizationSearchDto,
    signal: AbortSignal,
  ): Promise<OrganizationUnitPageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<OrganizationUnitDetailsDto>;
}
