import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { PayrollAmendmentReceiptDto } from "../models/payroll-amendment-command-dto";

export function toPayrollAmendmentReceipt(
  raw: PayrollAmendmentReceiptDto,
  id: string,
  expectedVersion: number | null,
): MutationReceipt {
  if (raw.id.toLowerCase() !== id || raw.version !== (expectedVersion ?? -1) + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id, version: raw.version });
}
