import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { HeadcountReport } from "../../domain/entities/headcount-report";
import type { HeadcountReportDto } from "../models/headcount-report-dto";

export function toHeadcountReport(
  dto: HeadcountReportDto,
  companyId: CompanyId,
  asOf: string,
): HeadcountReport {
  if (
    dto.companyId !== companyId ||
    dto.asOf !== asOf ||
    dto.persons > dto.employments ||
    dto.active + dto.probation + dto.suspended !== dto.employments ||
    dto.permanent + dto.fixedTerm !== dto.employments
  ) {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({ ...dto, companyId });
}
