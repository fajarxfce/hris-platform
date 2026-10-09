import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { HeadcountCounts } from "../../domain/entities/headcount-counts";
import type { HeadcountCountsDto } from "../models/headcount-report-dto";

export function toHeadcountCounts(dto: HeadcountCountsDto): HeadcountCounts {
  if (
    dto.persons > dto.employments ||
    (dto.persons === 0) !== (dto.employments === 0) ||
    dto.active + dto.probation + dto.suspended !== dto.employments ||
    dto.permanent + dto.fixedTerm !== dto.employments
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto });
}
