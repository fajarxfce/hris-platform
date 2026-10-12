import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceCorrectionPage } from "../../domain/entities/attendance-correction-page";
import type { AttendanceCorrectionPageDto } from "../models/attendance-correction-page-dto";
import { toAttendanceCorrection } from "./attendance-mapper";

export function toAttendanceCorrectionPage(
  dto: AttendanceCorrectionPageDto,
  company: CompanyId,
  employee: EmployeeId,
  workDate: string,
  after: string | null,
): AttendanceCorrectionPage {
  const items = dto.items.map(toAttendanceCorrection);
  const identifiers = new Set<string>();
  let previous = after === null ? Infinity : Number(after);
  for (const item of items) {
    if (item.version >= previous || identifiers.has(item.id)) throw new InvalidHttpResponseError();
    identifiers.add(item.id);
    previous = item.version;
  }
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 || previous === 0 || dto.nextCursor !== String(previous))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    employeeId: employee,
    workDate,
    items: Object.freeze(items),
    nextCursor: dto.nextCursor,
  });
}
