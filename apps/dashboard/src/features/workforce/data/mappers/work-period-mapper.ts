import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { JobId } from "../../../jobs/domain/entities/background-job";
import type { WorkPeriod, WorkPeriodId } from "../../domain/entities/work-period";
import { isWorkPeriodMonth } from "../../domain/policies/work-period-policy";
import type { WorkPeriodDto } from "../models/work-period-dto";

export function toWorkPeriod(dto: WorkPeriodDto, company: CompanyId, month: string): WorkPeriod {
  if (dto.month !== month || !isWorkPeriodMonth(month)) throw new InvalidHttpResponseError();
  if (dto.status === "OPEN") {
    if (
      dto.timezone !== null ||
      dto.jobId !== null ||
      dto.version !== 0 ||
      dto.startedAt !== null ||
      dto.closedAt !== null ||
      dto.failureCode !== null
    )
      throw new InvalidHttpResponseError();
  } else {
    if (
      dto.timezone === null ||
      dto.jobId === null ||
      dto.version < 1 ||
      dto.startedAt === null ||
      utcInstantMicroseconds(dto.startedAt) === null
    )
      throw new InvalidHttpResponseError();
    try {
      new Intl.DateTimeFormat("en", { timeZone: dto.timezone });
    } catch {
      throw new InvalidHttpResponseError();
    }
  }
  if ((dto.status === "CLOSED") !== (dto.closedAt !== null)) throw new InvalidHttpResponseError();
  if (dto.closedAt !== null) {
    const closed = utcInstantMicroseconds(dto.closedAt),
      started = utcInstantMicroseconds(dto.startedAt ?? "");
    if (closed === null || started === null || closed < started || dto.failureCode !== null)
      throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase() as WorkPeriodId,
    companyId: company,
    jobId: dto.jobId === null ? null : (dto.jobId.toLowerCase() as JobId),
  });
}
