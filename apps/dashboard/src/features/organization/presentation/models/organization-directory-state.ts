import type { Failure } from "../../../../core/domain/result";
import type { OrganizationUnitPage } from "../../domain/entities/organization-unit-page";

export type OrganizationDirectoryState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: OrganizationUnitPage | null;
  failure: Failure | null;
}>;
export const initialOrganizationDirectoryState: OrganizationDirectoryState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
