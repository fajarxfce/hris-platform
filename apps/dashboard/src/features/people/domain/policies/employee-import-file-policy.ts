import type { TextFile } from "../../../../core/domain/files/text-file";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeImportStart } from "../entities/employee-import-start";
import { employeeImportChangeWasRejected } from "./employee-import-change-policy";

export const employeeImportMaximumBytes = 524_288;
export function validateEmployeeImportFile(file: TextFile): Result<TextFile> {
  if (
    !file.name.trim() ||
    file.name.length > 120 ||
    !file.name.toLowerCase().endsWith(".csv") ||
    /[\p{Cc}/\\]/u.test(file.name)
  )
    return failed("employee_import_file_invalid");
  if (
    !Number.isSafeInteger(file.byteLength) ||
    file.byteLength < 1 ||
    file.byteLength > employeeImportMaximumBytes ||
    file.text.length === 0 ||
    file.text.length > employeeImportMaximumBytes
  )
    return failed("employee_import_size_limit");
  return success(file);
}
export function normalizeEmployeeImportStart(
  input: EmployeeImportStart,
): Result<EmployeeImportStart> {
  const file = validateEmployeeImportFile(input.file);
  if (!file.ok) return file;
  const reason = input.reason.trim();
  if (!isUuid(input.id) || reason.length < 1 || reason.length > 1000)
    return failed("invalid_employee_import");
  return success(Object.freeze({ id: input.id.toLowerCase(), file: file.value, reason }));
}
export const employeeImportStartWasRejected = (failure: Failure): boolean =>
  employeeImportChangeWasRejected(failure) ||
  [
    "employee_import_file_invalid",
    "employee_import_size_limit",
    "invalid_employee_csv",
    "request_body_too_large",
  ].includes(failure.code);
