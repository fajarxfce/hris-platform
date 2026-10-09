import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { JobId } from "../../domain/entities/background-job";
import type { JobPage } from "../../domain/entities/job-page";
import type { JobSearch } from "../../domain/entities/job-search";
import type { JobPageDto } from "../models/job-dto";
import { toBackgroundJob } from "./job-mapper";

export function toJobPage(dto: JobPageDto, companyId: CompanyId, search: JobSearch): JobPage {
  const items = dto.items.map((job) => toBackgroundJob(job, companyId));
  if (
    (dto.nextCreatedAt === null) !== (dto.nextId === null) ||
    new Set(items.map((job) => job.id)).size !== items.length
  )
    throw new InvalidHttpResponseError();
  let beforeAt = search.beforeAt === null ? null : utcInstantMicroseconds(search.beforeAt);
  let beforeId = search.beforeId;
  for (const item of items) {
    const at = utcInstantMicroseconds(item.createdAt);
    if (
      at === null ||
      (beforeAt !== null &&
        (at > beforeAt || (at === beforeAt && beforeId !== null && item.id >= beforeId)))
    )
      throw new InvalidHttpResponseError();
    beforeAt = at;
    beforeId = item.id;
  }
  const last = items.at(-1);
  const nextId = dto.nextId?.toLowerCase() as JobId | undefined;
  if (
    nextId !== undefined &&
    (items.length !== 50 ||
      last?.id !== nextId ||
      utcInstantMicroseconds(dto.nextCreatedAt ?? "") !== beforeAt)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId,
    items: Object.freeze(items),
    next:
      dto.nextCreatedAt === null || dto.nextId === null
        ? null
        : Object.freeze({
            beforeAt: dto.nextCreatedAt,
            beforeId: dto.nextId.toLowerCase() as JobId,
          }),
  });
}
