import type { OrganizationUnit } from "./organization-unit";

export type OrganizationUnitPage = Readonly<{
  items: readonly OrganizationUnit[];
  nextCursor: string | null;
}>;
