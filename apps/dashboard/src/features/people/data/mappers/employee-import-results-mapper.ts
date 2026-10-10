import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { OrganizationUnitId } from "../../../organization/domain/entities/organization-unit";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmployeeImportAttempts } from "../../domain/entities/employee-import-attempt";
import type {
  EmployeeImportRow,
  EmployeeImportRows,
} from "../../domain/entities/employee-import-row";
import type {
  EmployeeImportAttemptsDto,
  EmployeeImportRowDto,
  EmployeeImportRowsDto,
} from "../models/employee-import-dto";

export function toEmployeeImportRow(dto: EmployeeImportRowDto): EmployeeImportRow {
  const proposed = dto.proposed;
  if (
    (["READY", "APPLIED", "REJECTED"].includes(dto.status) && !proposed) ||
    (dto.status === "APPLIED"
      ? dto.createdEmploymentId?.toLowerCase() !== proposed?.employeeId.toLowerCase()
      : dto.createdEmploymentId !== null) ||
    (proposed &&
      (proposed.employeeNumber !== dto.employeeNumber || proposed.legalName !== dto.legalName))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    issues: Object.freeze({ ...dto.issues }),
    createdEmploymentId: dto.createdEmploymentId?.toLowerCase() ?? null,
    proposed: proposed
      ? Object.freeze({
          ...proposed,
          employeeId: proposed.employeeId.toLowerCase(),
          terms: Object.freeze({
            ...proposed.terms,
            branchId: (proposed.terms.branchId?.toLowerCase() as OrganizationUnitId | null) ?? null,
            departmentId:
              (proposed.terms.departmentId?.toLowerCase() as OrganizationUnitId | null) ?? null,
            positionId:
              (proposed.terms.positionId?.toLowerCase() as OrganizationUnitId | null) ?? null,
            costCenterId:
              (proposed.terms.costCenterId?.toLowerCase() as OrganizationUnitId | null) ?? null,
            managerId: (proposed.terms.managerId?.toLowerCase() as EmployeeId | null) ?? null,
          }),
        })
      : null,
  });
}
export function toEmployeeImportRows(
  dto: EmployeeImportRowsDto,
  after: number | null,
): EmployeeImportRows {
  const items = dto.items.map(toEmployeeImportRow);
  if (
    items.some(
      (item, index) =>
        item.number <= (after ?? 0) ||
        (index > 0 && item.number <= (items[index - 1]?.number ?? 0)),
    ) ||
    (dto.nextCursor !== null &&
      (items.length !== 25 || dto.nextCursor !== String(items.at(-1)?.number)))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
export function toEmployeeImportAttempts(
  dto: EmployeeImportAttemptsDto,
  after: string | null,
): EmployeeImportAttempts {
  const items = dto.items.map((item) =>
    Object.freeze({
      ...item,
      jobId: item.jobId.toLowerCase(),
      actorId: item.actorId.toLowerCase() as AccountId,
    }),
  );
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        utcInstantMicroseconds(item.createdAt) === null ||
        (after !== null && item.jobId <= after) ||
        (index > 0 && item.jobId <= (items[index - 1]?.jobId ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 10 || nextCursor !== items.at(-1)?.jobId))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
