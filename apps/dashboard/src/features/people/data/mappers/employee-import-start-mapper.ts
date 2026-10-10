import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { EmployeeImportReceiptDto } from "../models/employee-import-change-dto";

export function toEmployeeImportStartedReceipt(
  dto: EmployeeImportReceiptDto,
  id: string,
): MutationReceipt {
  if (dto.id.toLowerCase() !== id || dto.version !== 0) throw new InvalidHttpResponseError();
  return Object.freeze({ id, version: dto.version });
}
