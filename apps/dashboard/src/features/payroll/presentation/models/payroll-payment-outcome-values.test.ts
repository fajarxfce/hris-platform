import { describe, expect, it } from "vitest";
import type { PayrollPaymentOutcome } from "../../domain/entities/payroll-payment-outcome";
import {
  parsePayrollPaymentOutcomeValues,
  payrollPaymentOutcomeValues,
} from "./payroll-payment-outcome-values";

const itemId = "20000000-0000-4000-8000-000000000001";
const original: PayrollPaymentOutcome = {
  itemId,
  status: "SUCCEEDED",
  transactionReference: "BANK-1",
  occurredAt: "2026-10-13T06:01:00.987654Z",
  reason: "Bank confirmed",
  confirmedNoTransfer: false,
};
describe("company-time bank result input", () => {
  it("uses the company zone and preserves unchanged bank time precision", () => {
    const values = payrollPaymentOutcomeValues(original, "Asia/Jakarta");
    expect(values.occurredAt).toBe("2026-10-13T13:01:00");
    expect(parsePayrollPaymentOutcomeValues(values, itemId, "Asia/Jakarta", original)).toEqual(
      original,
    );
    expect(
      parsePayrollPaymentOutcomeValues(
        { ...values, occurredAt: "2026-10-13T13:02:00" },
        itemId,
        "Asia/Jakarta",
        original,
      )?.occurredAt,
    ).toBe("2026-10-13T06:02:00Z");
  });
  it("requires an explicit result and valid unambiguous local time", () => {
    const empty = payrollPaymentOutcomeValues(null, "America/New_York");
    expect(parsePayrollPaymentOutcomeValues(empty, itemId, "America/New_York", null)).toBeNull();
    for (const occurredAt of ["2026-03-08T02:30:00", "2026-11-01T01:30:00", "2026-02-30T12:00:00"])
      expect(
        parsePayrollPaymentOutcomeValues(
          { ...empty, status: "SUCCEEDED", occurredAt },
          itemId,
          "America/New_York",
          null,
        ),
      ).toBeNull();
  });
  it("does not carry a success reference into a failed result or infer no-transfer confirmation", () => {
    const values = payrollPaymentOutcomeValues(original, "Asia/Jakarta");
    expect(
      parsePayrollPaymentOutcomeValues(
        { ...values, status: "FAILED" },
        itemId,
        "Asia/Jakarta",
        original,
      ),
    ).toMatchObject({ transactionReference: null, confirmedNoTransfer: false });
    expect(
      parsePayrollPaymentOutcomeValues(
        { ...values, status: "FAILED", confirmedNoTransfer: true },
        itemId,
        "Asia/Jakarta",
        original,
      ),
    ).toMatchObject({ transactionReference: null, confirmedNoTransfer: true });
    expect(
      parsePayrollPaymentOutcomeValues(
        { ...values, confirmedNoTransfer: true },
        itemId,
        "Asia/Jakarta",
        original,
      ),
    ).toMatchObject({ status: "SUCCEEDED", confirmedNoTransfer: false });
  });
});
