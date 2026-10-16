import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  RoleTemplate,
  RoleTemplateId,
  RoleTemplatePage,
} from "../../domain/entities/role-template";
import type { RoleTemplateReview } from "../../domain/entities/role-template-review";
import type {
  RoleTemplateDto,
  RoleTemplatePageDto,
  RoleTemplateReviewDto,
} from "../models/role-template-dto";

export function toRolePermissions(values: readonly string[]): readonly string[] {
  if (new Set(values).size !== values.length) throw new InvalidHttpResponseError();
  return Object.freeze([...values].sort());
}
export function toRoleTemplate(raw: RoleTemplateDto, companyId: CompanyId): RoleTemplate {
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase() as RoleTemplateId,
    companyId,
    permissions: toRolePermissions(raw.permissions),
  });
}
export function toRoleTemplatePage(
  raw: RoleTemplatePageDto,
  company: CompanyId,
  after: string | null,
): RoleTemplatePage {
  const items = raw.items.map((item) => toRoleTemplate(item, company));
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(items.map((item) => item.code)).size !== items.length ||
    items.some(
      (item, index) => item.id <= (index === 0 ? (after ?? "") : (items[index - 1]?.id ?? "")),
    ) ||
    (cursor !== null && (items.length !== 20 || cursor !== items.at(-1)?.id || cursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
export function toRoleTemplateReview(
  raw: RoleTemplateReviewDto,
  company: CompanyId,
  id: RoleTemplateId,
  after: string | null,
): RoleTemplateReview {
  const current = toRoleTemplate(raw.current, company);
  if (current.id !== id || (after !== null && Number(after) > current.version))
    throw new InvalidHttpResponseError();
  let previous = after === null ? current.version + 1 : Number(after);
  const items = raw.history.items.map((row) => {
    if (
      row.revision !== previous - 1 ||
      row.code !== current.code ||
      !Number.isFinite(Date.parse(row.recordedAt))
    )
      throw new InvalidHttpResponseError();
    previous = row.revision;
    const permissions = toRolePermissions(row.permissions);
    if (
      row.revision === current.version &&
      (row.name !== current.name ||
        row.active !== current.active ||
        permissions.join("|") !== current.permissions.join("|"))
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({ ...row, actorId: row.actorId.toLowerCase(), permissions });
  });
  const cursor = raw.history.nextCursor;
  if (
    (items.length === 0 && after !== "0") ||
    (cursor === null && items.length > 0 && items.at(-1)?.revision !== 0) ||
    (cursor !== null &&
      (items.length !== 20 ||
        cursor !== String(items.at(-1)?.revision) ||
        cursor === after ||
        cursor === "0"))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    current,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
  });
}
