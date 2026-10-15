import { sha256 } from "@noble/hashes/sha2.js";
import { bytesToHex } from "@noble/hashes/utils.js";
import type {
  PayrollPaymentProgressDto,
  PayrollPayslipDto,
  PayrollPayslipSummaryDto,
} from "../../src/features/payroll/data/models/payroll-payslip-dto";
import { calculationEmployee, calculationEmployeeId } from "./payroll-calculations";

export const payslipId = (index = 1) =>
  `f1000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export function payslipSummary(
  index = 1,
  changes: Partial<PayrollPayslipSummaryDto> = {},
): PayrollPayslipSummaryDto {
  return {
    id: payslipId(index),
    employeeId: calculationEmployeeId(index),
    employeeNumber: `EMP-${String(index).padStart(4, "0")}`,
    employeeName: `Example employee ${index}`,
    taxMonth: "2026-09",
    plannedPaymentDate: "2026-10-02",
    currency: "IDR",
    taxableGross: "10000000.25",
    withheld: "-0.50",
    takeHome: "10000000.75",
    publishedAt: "2026-10-01T01:00:00Z",
    version: 0,
    ...changes,
  };
}
export function payslipDetails(index = 1): PayrollPayslipDto {
  const calculation = calculationEmployee().calculation;
  if (!calculation) throw new Error("Fixture calculation required");
  calculation.contributions = [
    { program: "HEALTH", base: "10000000.25", employeeAmount: "100000", employerAmount: "400000" },
  ];
  return {
    summary: payslipSummary(index),
    companyCode: "DEMO",
    companyName: "Demo Company",
    calculation,
  };
}
export function payslipPayments(index = 1): PayrollPaymentProgressDto {
  return {
    assessmentId: payslipId(index),
    version: 5,
    settlement: {
      assessmentRevision: 0,
      entitlement: "10000000.75",
      confirmedPaid: "0.00",
      confirmedRecovered: "0.00",
      payable: "10000000.75",
      recoverable: "0.00",
      currency: "IDR",
    },
    attempts: [
      {
        batchId: "f2000000-0000-4000-8000-000000000002",
        itemId: "f3000000-0000-4000-8000-000000000002",
        status: "PENDING",
        amount: "10000000.75",
        assessmentRevision: 0,
        createdAt: "2026-10-02T02:00:00Z",
        releasedAt: "2026-10-02T02:01:00Z",
        resolvedAt: null,
      },
      {
        batchId: "f2000000-0000-4000-8000-000000000001",
        itemId: "f3000000-0000-4000-8000-000000000001",
        status: "FAILED",
        amount: "10000000.75",
        assessmentRevision: 0,
        createdAt: "2026-10-02T01:00:00Z",
        releasedAt: "2026-10-02T01:01:00Z",
        resolvedAt: "2026-10-02T01:02:00Z",
      },
    ],
  };
}
export function payslipPdf() {
  const bytes = new Uint8Array(
    new TextEncoder().encode("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"),
  );
  return {
    parts: [bytes.buffer] as [ArrayBuffer],
    byteLength: bytes.byteLength,
    mediaType: "application/pdf",
    etag: `"${bytesToHex(sha256(bytes))}"`,
  };
}
