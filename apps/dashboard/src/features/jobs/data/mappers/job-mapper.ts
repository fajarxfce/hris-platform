import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { BackgroundJob, JobId } from "../../domain/entities/background-job";
import type { JobDto } from "../models/job-dto";

export function toBackgroundJob(dto: JobDto, companyId: CompanyId, id?: JobId): BackgroundJob {
  const jobId = dto.id.toLowerCase() as JobId;
  const terminal =
    dto.status === "SUCCEEDED" || dto.status === "FAILED" || dto.status === "CANCELLED";
  if (
    (id !== undefined && jobId !== id) ||
    dto.completedItems > dto.totalItems ||
    (dto.status === "SUCCEEDED" &&
      dto.progressMode === "FIXED_TOTAL" &&
      dto.completedItems !== dto.totalItems) ||
    terminal !== (dto.finishedAt !== null) ||
    (dto.availableActions.includes("cancel") && (terminal || dto.cancellationRequested)) ||
    new Set(dto.availableActions).size !== dto.availableActions.length ||
    [dto.createdAt, dto.finishedAt, dto.scheduledFor, dto.availableAt].some(
      (value) => value !== null && utcInstantMicroseconds(value) === null,
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    companyId,
    id: jobId,
    availableActions: Object.freeze([...dto.availableActions]),
  });
}
