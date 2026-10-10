import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OrganizationUnit } from "./organization-unit";

export type OrganizationUnitDetails = Readonly<{
  companyId: CompanyId;
  unit: OrganizationUnit;
  parent: OrganizationUnit | null;
}>;
