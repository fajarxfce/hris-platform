import { describe, expect, it } from "vitest";
import { payrollIdentifier } from "../../../../../tests/fixtures/payroll-payment-identifiers";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { payrollPaymentRecoveryRoute } from "./payroll-payment-recovery-route";

const company = payrollIdentifier(1) as CompanyId,
  access = { companyId: company, permissions: ["payroll.pay"] },
  assessment = payrollIdentifier(3);
describe("return review navigation", () => {
  it("retains only a scoped batch and selected payment for the return path", () => {
    const route = payrollPaymentRecoveryRoute(
      assessment,
      access,
      new URLSearchParams({
        company: payrollIdentifier(2),
        batch: payrollIdentifier(4),
        payment: payrollIdentifier(5),
        redirect: "https://outside.example/private",
        entry: payrollIdentifier(6),
      }),
    );
    expect(route.backTo).toBe(
      `/payroll/payments/${payrollIdentifier(4)}?company=${company}&view=results&item=${payrollIdentifier(5)}`,
    );
    expect(route.record).toContain(`/returns/new?company=${company}&batch=`);
    expect(route.record).not.toContain("redirect");
    expect(route.record).not.toContain("entry");
  });
  it("does not redirect finance-only operators to an unauthorized payslip", () => {
    const route = payrollPaymentRecoveryRoute(
      assessment,
      access,
      new URLSearchParams({ batch: "../other", source: "payslip", scope: "SELF" }),
    );
    expect(route.backTo).toBe(`/payroll/payments?company=${company}`);
    expect(route.backLabel).toBe("backPayments");
  });
  it("returns authorized payslip readers to the same scope and payment tab", () => {
    const route = payrollPaymentRecoveryRoute(
      assessment,
      { ...access, permissions: ["payroll.pay", "payroll.read"] },
      new URLSearchParams({ source: "payslip", scope: "SELF" }),
    );
    expect(route.backTo).toBe(
      `/payroll/payslips/${assessment}?company=${company}&scope=SELF&tab=payments`,
    );
    expect(route.backLabel).toBe("backPayslip");
  });
  it("retains an explicit payslip revision through return review and entry", () => {
    const route = payrollPaymentRecoveryRoute(
      assessment,
      { ...access, permissions: ["payroll.pay", "payroll.self.read"] },
      new URLSearchParams({ source: "payslip", scope: "SELF", revision: "0" }),
    );
    expect(route.backTo).toContain("scope=SELF&revision=0&tab=payments");
    expect(route.review).toContain("revision=0");
    expect(route.record).toContain("revision=0");
  });
});
