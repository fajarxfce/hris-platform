import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { EmployeeImportChange } from "../../domain/entities/employee-import-change";
import type { EmployeeImportReceiptDto } from "../models/employee-import-change-dto";

export function toEmployeeImportReceipt(
  dto: EmployeeImportReceiptDto,
  change: EmployeeImportChange,
): MutationReceipt {
  if (dto.id.toLowerCase() !== change.importId || dto.version !== change.expectedVersion + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
