import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  EmployeeImport,
  EmployeeImportId,
  EmployeeImportPage,
} from "../../domain/entities/employee-import";
import type { EmployeeImportSummary } from "../../domain/entities/employee-import-summary";
import type {
  EmployeeImportDto,
  EmployeeImportPageDto,
  EmployeeImportSummaryDto,
} from "../models/employee-import-dto";

export function toEmployeeImport(dto: EmployeeImportDto, companyId: CompanyId): EmployeeImport {
  if (!dto.fileName.trim() || !dto.reason.trim() || utcInstantMicroseconds(dto.createdAt) === null)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase() as EmployeeImportId,
    companyId,
    jobId: dto.jobId.toLowerCase(),
    createdBy: dto.createdBy.toLowerCase() as AccountId,
  });
}
export function toEmployeeImportPage(
  dto: EmployeeImportPageDto,
  company: CompanyId,
  after: string | null,
): EmployeeImportPage {
  const items = dto.items.map((item) => toEmployeeImport(item, company));
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        (after !== null && item.id <= after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 10 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
export function toEmployeeImportSummary(
  dto: EmployeeImportSummaryDto,
  company: CompanyId,
  id: EmployeeImportId,
): EmployeeImportSummary {
  const batch = toEmployeeImport(dto.batch, company);
  const counts = Object.freeze({
    PENDING: 0,
    READY: 0,
    INVALID: 0,
    APPLIED: 0,
    REJECTED: 0,
    ...dto.counts,
  });
  if (
    batch.id !== id ||
    Object.values(counts).reduce((sum, count) => sum + count, 0) !== batch.rowCount
  )
    throw new InvalidHttpResponseError();
  if (new Set(dto.availableActions).size !== dto.availableActions.length)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    batch,
    counts,
    jobStatus: dto.jobStatus,
    cancellationRequested: dto.cancellationRequested,
    availableActions: Object.freeze([...dto.availableActions]),
  });
}
