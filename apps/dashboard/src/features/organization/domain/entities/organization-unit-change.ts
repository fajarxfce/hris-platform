import type { OrganizationUnitKind } from "./organization-unit";

export type OrganizationUnitChange = Readonly<{
  id: string;
  code: string;
  name: string;
  kind: OrganizationUnitKind;
  parentId: string | null;
  timezone: string | null;
  active: boolean;
  expectedVersion: number | null;
}>;
