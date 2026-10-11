import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AudienceReferencePage } from "../../domain/entities/audience-reference";
import type { AudienceReferenceSearch } from "../../domain/entities/audience-reference-search";
import type { AudienceReferencePageDto } from "../models/audience-reference-dto";

export function toAudienceReferencePage(
  dto: AudienceReferencePageDto,
  search: AudienceReferenceSearch,
): AudienceReferencePage {
  let previous = search.after;
  const items = dto.items.map((raw) => {
    const id = raw.id.toLowerCase();
    if (
      raw.kind !== search.kind ||
      !raw.name.trim() ||
      (previous !== null && id <= previous) ||
      (search.ids.length > 0 && !search.ids.includes(id)) ||
      (raw.kind === "EMPLOYMENT" ? raw.active !== null : raw.active === null) ||
      (raw.kind === "GROUP" ? raw.code !== null : raw.code === null) ||
      (search.ids.length === 0 && raw.active === false)
    )
      throw new InvalidHttpResponseError();
    previous = id;
    return Object.freeze({
      id,
      kind: raw.kind,
      name: raw.name,
      code: raw.code,
      version: raw.version,
      active: raw.active,
    });
  });
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    nextCursor !== null &&
    (search.ids.length > 0 || items.length !== 50 || nextCursor !== items.at(-1)?.id)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
