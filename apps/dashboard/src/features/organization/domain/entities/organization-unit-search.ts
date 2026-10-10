import type { OrganizationUnitKind } from "./organization-unit";

/** External filter input is validated by the use case before repository access. */
export type OrganizationUnitSearchInput = Readonly<{
  query: string;
  kind: string | null;
  active: string | null;
  after: string | null;
}>;

export type OrganizationUnitSearch = Readonly<{
  query: string;
  kind: OrganizationUnitKind | null;
  active: boolean | null;
  after: string | null;
}>;
