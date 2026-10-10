import type { LoadOrganizationUnit } from "../../domain/usecases/load-organization-unit";
import type { LoadOrganizationUnits } from "../../domain/usecases/load-organization-units";

export type OrganizationUseCases = Readonly<{
  loadUnits: Pick<LoadOrganizationUnits, "execute">;
  loadUnit: Pick<LoadOrganizationUnit, "execute">;
}>;
