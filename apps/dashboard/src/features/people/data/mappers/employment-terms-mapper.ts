import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { EmploymentTerms } from "../../domain/entities/employment-terms";
import type { EmploymentTermsDto } from "../models/employment-terms-dto";

export function toEmploymentTerms(dto: EmploymentTermsDto): EmploymentTerms {
  if (
    !isCalendarDate(dto.effectiveFrom) ||
    !isCalendarDate(dto.startDate) ||
    dto.effectiveFrom < dto.startDate ||
    (dto.endDate !== null && (!isCalendarDate(dto.endDate) || dto.endDate < dto.startDate)) ||
    (dto.contract === "FIXED_TERM" && (dto.endDate === null || dto.status === "PROBATION")) ||
    (dto.status === "ENDED" && dto.endDate === null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto });
}
