import type { LoadOrganizationUnit } from "../../domain/usecases/load-organization-unit";
import type { LoadOrganizationUnits } from "../../domain/usecases/load-organization-units";
import type { SaveOrganizationUnit } from "../../domain/usecases/save-organization-unit";

export type OrganizationUseCases = Readonly<{
  loadUnits: Pick<LoadOrganizationUnits, "execute">;
  loadUnit: Pick<LoadOrganizationUnit, "execute">;
  saveUnit: Pick<SaveOrganizationUnit, "execute">;
}>;
