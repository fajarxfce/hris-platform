import type { CompanyId } from "../../../../core/domain/identifiers";

export type OrganizationUnitId = string & { readonly organizationUnitId: unique symbol };
export const organizationUnitKinds = ["BRANCH", "DEPARTMENT", "POSITION", "COST_CENTER"] as const;
export type OrganizationUnitKind = (typeof organizationUnitKinds)[number];

export type OrganizationUnit = Readonly<{
  id: OrganizationUnitId;
  companyId: CompanyId;
  code: string;
  name: string;
  kind: OrganizationUnitKind;
  parentId: OrganizationUnitId | null;
  timezone: string | null;
  active: boolean;
  version: number;
}>;
