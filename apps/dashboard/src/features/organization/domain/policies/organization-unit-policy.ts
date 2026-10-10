import { failed, type Result, success } from "../../../../core/domain/result";
import { type OrganizationUnitKind, organizationUnitKinds } from "../entities/organization-unit";
import type {
  OrganizationUnitSearch,
  OrganizationUnitSearchInput,
} from "../entities/organization-unit-search";

export const canReadOrganization = (permissions: readonly string[]): boolean =>
  permissions.includes("company.read");

export const isOrganizationUnitId = (id: string): boolean =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu.test(id);

export const isOrganizationUnitCode = (code: string): boolean =>
  /^[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(code);

export function parseOrganizationUnitSearch(
  input: OrganizationUnitSearchInput,
): Result<OrganizationUnitSearch> {
  if (input.query.length > 120) return failed("invalid_organization_search");
  if (
    (input.kind !== null && !organizationUnitKinds.some((kind) => kind === input.kind)) ||
    (input.active !== null && input.active !== "true" && input.active !== "false")
  )
    return failed("invalid_organization_filter");
  if (
    input.after !== null &&
    (!/^(BRANCH|DEPARTMENT|POSITION|COST_CENTER):[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(input.after) ||
      (input.kind !== null && !input.after.startsWith(`${input.kind}:`)))
  )
    return failed("invalid_page");
  return success(
    Object.freeze({
      query: input.query.trim(),
      kind: input.kind as OrganizationUnitKind | null,
      active: input.active === null ? null : input.active === "true",
      after: input.after,
    }),
  );
}
