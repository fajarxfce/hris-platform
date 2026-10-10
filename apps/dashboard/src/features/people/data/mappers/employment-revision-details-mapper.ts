import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentRevisionDetails } from "../../domain/entities/employment-revision-details";
import type { EmploymentRevisionDetailsDto } from "../models/employment-revision-details-dto";
import { toEmploymentRevision } from "./employment-history-mapper";

export function toEmploymentRevisionDetails(
  dto: EmploymentRevisionDetailsDto,
  id: EmployeeId,
  revision: number,
): EmploymentRevisionDetails {
  if (
    dto.employeeId.toLowerCase() !== id.toLowerCase() ||
    dto.revision.revision !== revision ||
    dto.version < revision ||
    !isCalendarDate(dto.companyDate) ||
    (dto.canCancel &&
      (revision === 0 ||
        dto.revision.cancellation !== null ||
        dto.revision.terms.effectiveFrom <= dto.companyDate))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    employeeId: dto.employeeId.toLowerCase() as EmployeeId,
    version: dto.version,
    companyDate: dto.companyDate,
    revision: toEmploymentRevision(dto.revision),
    canCancel: dto.canCancel,
  });
}
