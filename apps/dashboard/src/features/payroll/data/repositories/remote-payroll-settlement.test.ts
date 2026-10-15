import { assert, describe, expect, it, vi } from "vitest";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId, payslipPayments } from "../../../../../tests/fixtures/payroll-payslips";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { HttpPayrollPayslipDataSource } from "../datasources/http-payroll-payslip-data-source";
import type { PayrollPaymentProgressDto } from "../models/payroll-payslip-dto";
import { RemotePayrollPayslipRepository } from "./remote-payroll-payslip-repository";

const company = calculationCompany as CompanyId;
function acquired(raw: unknown) {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
  return new RemotePayrollPayslipRepository(new HttpPayrollPayslipDataSource({ request })).payments(
    company,
    payslipId(),
    "SELF",
    new AbortController().signal,
  );
}
function revised(): PayrollPaymentProgressDto {
  const raw = payslipPayments();
  raw.version = 7;
  const paid = raw.attempts[0];
  assert(paid);
  paid.status = "SUCCEEDED";
  paid.resolvedAt = "2026-10-02T03:00:00Z";
  raw.settlement = {
    assessmentRevision: 1,
    entitlement: "11000000.75",
    confirmedPaid: "10000000.75",
    confirmedRecovered: "0.00",
    payable: "1000000.00",
    recoverable: "0.00",
    currency: "IDR",
  };
  return raw;
}
describe("independent payroll settlement projection", () => {
  it("retains an amended entitlement and prior successful transfer without requiring a payslip read", async () => {
    const raw = revised(),
      result = await acquired(raw);
    assert(result.ok);
    expect(result.value.settlement).toEqual(raw.settlement);
    expect(result.value.attempts[0]).toMatchObject({
      assessmentRevision: 0,
      amount: "10000000.75",
    });
    expect(Object.isFrozen(result.value.settlement)).toBe(true);
    raw.settlement.payable = "1.00";
    expect(result.value.settlement.payable).toBe("1000000.00");
  });
  it("retains a partially returned overpayment as a positive recoverable balance", async () => {
    const raw = revised();
    raw.version++;
    raw.settlement = {
      ...raw.settlement,
      entitlement: "9000000.75",
      confirmedRecovered: "400000.25",
      payable: "0.00",
      recoverable: "599999.75",
    };
    const result = await acquired(raw);
    assert(result.ok);
    expect(result.value.settlement.recoverable).toBe("599999.75");
    expect(result.value.settlement.confirmedPaid).toBe("10000000.75");
  });
  it.each<[string, (raw: PayrollPaymentProgressDto) => void]>([
    [
      "missing balance",
      (raw) => {
        Reflect.deleteProperty(raw, "settlement");
      },
    ],
    [
      "revision beyond head",
      (raw) => {
        raw.settlement.assessmentRevision = raw.version + 1;
      },
    ],
    [
      "future instruction",
      (raw) => {
        const first = raw.attempts[0];
        assert(first);
        first.assessmentRevision = 2;
      },
    ],
    [
      "different confirmed sum",
      (raw) => {
        raw.settlement.confirmedPaid = "10000000.74";
      },
    ],
    [
      "contradictory payable",
      (raw) => {
        raw.settlement.payable = "1000000.01";
      },
    ],
    [
      "payable and recoverable",
      (raw) => {
        raw.settlement.recoverable = "1.00";
      },
    ],
    [
      "return exceeding transfers",
      (raw) => {
        raw.settlement.confirmedRecovered = "20000000.00";
        raw.settlement.payable = "21000000.00";
      },
    ],
    [
      "zero transfer",
      (raw) => {
        const first = raw.attempts[0];
        assert(first);
        first.amount = "0.00";
      },
    ],
    [
      "floating point money",
      (raw) => {
        Object.assign(raw.settlement, { entitlement: 11000000.75 });
      },
    ],
  ])("rejects %s through the data failure boundary", async (_, corrupt) => {
    const raw = revised();
    corrupt(raw);
    expect(await acquired(raw)).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
});
