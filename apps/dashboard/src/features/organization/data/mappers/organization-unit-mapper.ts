import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OrganizationUnit, OrganizationUnitId } from "../../domain/entities/organization-unit";
import type { OrganizationUnitDetails } from "../../domain/entities/organization-unit-details";
import type { OrganizationUnitPage } from "../../domain/entities/organization-unit-page";
import type { OrganizationUnitSearch } from "../../domain/entities/organization-unit-search";
import { isOrganizationUnitCode } from "../../domain/policies/organization-unit-policy";
import type {
  OrganizationUnitDetailsDto,
  OrganizationUnitDto,
  OrganizationUnitPageDto,
} from "../models/organization-unit-dto";

export function toOrganizationUnit(
  dto: OrganizationUnitDto,
  companyId: CompanyId,
): OrganizationUnit {
  if (
    !isOrganizationUnitCode(dto.code) ||
    dto.name.trim().length === 0 ||
    dto.id.toLowerCase() === dto.parentId?.toLowerCase() ||
    (dto.kind === "BRANCH" ? dto.timezone === null : dto.timezone !== null)
  ) {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    id: dto.id.toLowerCase() as OrganizationUnitId,
    companyId,
    code: dto.code,
    name: dto.name,
    kind: dto.kind,
    parentId: dto.parentId === null ? null : (dto.parentId.toLowerCase() as OrganizationUnitId),
    timezone: dto.timezone,
    active: dto.active,
    version: dto.version,
  });
}

export function toOrganizationUnitPage(
  dto: OrganizationUnitPageDto,
  companyId: CompanyId,
  search: OrganizationUnitSearch,
): OrganizationUnitPage {
  const items = dto.items.map((item) => toOrganizationUnit(item, companyId));
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => `${item.kind}:${item.code}`)).size !== items.length ||
    items.some(
      (item) =>
        (search.kind !== null && item.kind !== search.kind) ||
        (search.active !== null && item.active !== search.active) ||
        `${item.kind}:${item.code}` === search.after,
    ) ||
    (dto.nextCursor !== null &&
      (items.length !== 50 || dto.nextCursor !== `${items.at(-1)?.kind}:${items.at(-1)?.code}`))
  ) {
    throw new InvalidHttpResponseError();
  }
  // Tuple order follows PostgreSQL collation; the client must not substitute JS lexical order.
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}

export function toOrganizationUnitDetails(
  dto: OrganizationUnitDetailsDto,
  companyId: CompanyId,
  id: OrganizationUnitId,
): OrganizationUnitDetails {
  const unit = toOrganizationUnit(dto.unit, companyId);
  const parent = dto.parent === null ? null : toOrganizationUnit(dto.parent, companyId);
  if (
    dto.companyId.toLowerCase() !== companyId ||
    unit.id !== id ||
    unit.parentId !== (parent?.id ?? null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ companyId, unit, parent });
}
