import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import type { LifecycleTemplatePage } from "../../domain/entities/lifecycle-template-page";
import {
  isLifecycleTaskKey,
  isLifecycleTemplateCode,
} from "../../domain/policies/lifecycle-template-policy";
import type {
  LifecycleTemplateDto,
  LifecycleTemplatePageDto,
} from "../models/lifecycle-template-dto";

export function toLifecycleTemplate(
  dto: LifecycleTemplateDto,
  companyId: CompanyId,
): LifecycleTemplate {
  if (
    !isLifecycleTemplateCode(dto.code) ||
    dto.name.trim().length === 0 ||
    new Set(dto.tasks.map((task) => task.key)).size !== dto.tasks.length ||
    dto.tasks.some((task) => !isLifecycleTaskKey(task.key) || task.title.trim().length === 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as LifecycleTemplateId,
    companyId,
    code: dto.code,
    name: dto.name,
    kind: dto.kind,
    active: dto.active,
    version: dto.version,
    tasks: Object.freeze(
      dto.tasks.map((task) =>
        Object.freeze({
          key: task.key,
          title: task.title,
          required: task.required,
          dueDays: task.dueDays,
        }),
      ),
    ),
  });
}
export function toLifecycleTemplateDetails(
  dto: LifecycleTemplateDto,
  companyId: CompanyId,
  id: LifecycleTemplateId,
): LifecycleTemplate {
  const template = toLifecycleTemplate(dto, companyId);
  if (template.id !== id) throw new InvalidHttpResponseError();
  return template;
}
export function toLifecycleTemplatePage(
  dto: LifecycleTemplatePageDto,
  companyId: CompanyId,
  after: string | null,
): LifecycleTemplatePage {
  const items = dto.items.map((item) => toLifecycleTemplate(item, companyId));
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.code)).size !== items.length ||
    items.some((item) => item.code === after) ||
    (dto.nextCursor !== null && (items.length !== 20 || dto.nextCursor !== items.at(-1)?.code))
  )
    throw new InvalidHttpResponseError();
  // Code ordering belongs to the database collation, not JavaScript string comparison.
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
