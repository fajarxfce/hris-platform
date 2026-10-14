import { sha256 } from "@noble/hashes/sha2.js";
import { bytesToHex } from "@noble/hashes/utils.js";
import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import {
  type BinaryFileContent,
  binaryFilePartBytes,
} from "../../../../core/domain/files/binary-file";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPaymentProgress } from "../../domain/entities/payroll-payment-progress";
import type {
  PayrollPayslip,
  PayrollPayslipPage,
  PayrollPayslipSearch,
  PayrollPayslipSummary,
} from "../../domain/entities/payroll-payslip";
import type {
  PayrollPaymentProgressDto,
  PayrollPayslipDto,
  PayrollPayslipPageDto,
  PayrollPayslipSummaryDto,
} from "../models/payroll-payslip-dto";
import { toPayrollCalculationOutput } from "./payroll-calculation-output-mapper";
import { payrollCalculationMinor } from "./payroll-run-employee-mapper";

export function toPayrollPayslipSummary(
  raw: PayrollPayslipSummaryDto,
  company: CompanyId,
): PayrollPayslipSummary {
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    employeeId: raw.employeeId.toLowerCase(),
  });
}
export function toPayrollPayslipPage(
  raw: PayrollPayslipPageDto,
  company: CompanyId,
  search: PayrollPayslipSearch,
): PayrollPayslipPage {
  const items = raw.items.map((row) => toPayrollPayslipSummary(row, company));
  let month = search.after?.slice(0, 7) ?? search.until;
  let id = search.after?.slice(8) ?? "";
  const unique = new Set<string>();
  for (const row of items) {
    if (
      unique.has(row.id) ||
      row.taxMonth < search.from ||
      row.taxMonth > month ||
      (row.taxMonth === month && row.id <= id) ||
      (search.employeeId !== null && row.employeeId !== search.employeeId)
    )
      throw new InvalidHttpResponseError();
    unique.add(row.id);
    month = row.taxMonth;
    id = row.id;
  }
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (cursor !== null && (items.length !== 50 || cursor !== `${month}:${id}`))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    scope: search.scope,
    items: Object.freeze(items),
    nextCursor: cursor,
  });
}
export function toPayrollPayslip(
  raw: PayrollPayslipDto,
  company: CompanyId,
  id: string,
): PayrollPayslip {
  if (
    raw.summary.id.toLowerCase() !== id ||
    (["taxableGross", "withheld", "takeHome"] as const).some(
      (key) =>
        payrollCalculationMinor(raw.summary[key]) !==
        payrollCalculationMinor(raw.calculation.tax[key]),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    summary: toPayrollPayslipSummary(raw.summary, company),
    companyCode: raw.companyCode,
    companyName: raw.companyName,
    calculation: toPayrollCalculationOutput(raw.calculation),
  });
}
export function toPayrollPaymentProgress(
  raw: PayrollPaymentProgressDto,
  company: CompanyId,
  id: string,
): PayrollPaymentProgress {
  if (
    raw.assessmentId.toLowerCase() !== id ||
    new Set(raw.attempts.map((item) => item.itemId.toLowerCase())).size !== raw.attempts.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    assessmentId: id,
    version: raw.version,
    attempts: Object.freeze(
      raw.attempts.map((attempt) =>
        Object.freeze({
          ...attempt,
          batchId: attempt.batchId.toLowerCase(),
          itemId: attempt.itemId.toLowerCase(),
        }),
      ),
    ),
  });
}
export function toPayrollPayslipPdf(raw: BinaryResponseDto): BinaryFileContent {
  if (
    raw.byteLength > 1024 * 1024 ||
    raw.byteLength < 5 ||
    raw.mediaType !== "application/pdf" ||
    !/^"[a-f0-9]{64}"$/u.test(raw.etag) ||
    raw.parts.length < 1 ||
    raw.parts.length > 16 ||
    raw.parts.some((part) => part.byteLength < 1 || part.byteLength > binaryFilePartBytes) ||
    raw.parts.reduce((size, part) => size + part.byteLength, 0) !== raw.byteLength
  )
    throw new InvalidHttpResponseError();
  const digest = sha256.create();
  try {
    for (const part of raw.parts) digest.update(new Uint8Array(part));
    if (`"${bytesToHex(digest.digest())}"` !== raw.etag) throw new InvalidHttpResponseError();
    return Object.freeze({ byteLength: raw.byteLength, parts: Object.freeze([...raw.parts]) });
  } finally {
    digest.destroy();
  }
}
