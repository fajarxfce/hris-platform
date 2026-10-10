import type { Failure } from "../../../../core/domain/result";
import type { OrganizationUnitDetails } from "../../domain/entities/organization-unit-details";

export type OrganizationUnitState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  details: OrganizationUnitDetails | null;
  failure: Failure | null;
}>;
export const initialOrganizationUnitState: OrganizationUnitState = Object.freeze({
  stage: "idle",
  details: null,
  failure: null,
});
