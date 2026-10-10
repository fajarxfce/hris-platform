import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  employeeImportMaximumBytes,
  validateEmployeeImportFile,
} from "../policies/employee-import-file-policy";
import { canImportEmployees } from "../policies/employee-import-policy";

export class SelectEmployeeImportFile {
  constructor(private readonly files: FileRepository) {}
  async execute(access: CompanyAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions)) return failed("employee_import_access_required");
    const selected = await this.files.selectText(
      { accept: ".csv,text/csv", maximumBytes: employeeImportMaximumBytes },
      signal,
    );
    signal.throwIfAborted();
    if (!selected.ok || selected.value === null) return selected;
    return validateEmployeeImportFile(selected.value);
  }
}
