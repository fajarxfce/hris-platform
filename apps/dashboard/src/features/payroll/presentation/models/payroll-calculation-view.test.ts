import { describe, expect, it } from "vitest";
import {
  calculationCompany,
  calculationEmployee,
  calculationEmployeeId,
  calculationId,
  calculationPeriod,
} from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { toPayrollRunEmployee } from "../../data/mappers/payroll-run-employee-mapper";
import { payrollRunEmployeePath, payrollRunPath } from "./payroll-calculation-route";
import { payrollAmountView } from "./payroll-policy-view";
import { payrollRunEmployeeView } from "./payroll-run-employee-view";

describe("calculation display", () => {
  it.each(["en", "id"] as const)("shows the retained assessment revision in %s", (locale) => {
    const raw = calculationEmployee();
    raw.item.target.previousAssessmentId = calculationPeriod;
    raw.item.target.previousAssessmentRevision = 3;
    const view = payrollRunEmployeeView(
      toPayrollRunEmployee(
        raw,
        calculationCompany as CompanyId,
        calculationId,
        calculationEmployeeId(),
      ),
      locale,
    );
    expect(view.references).toContainEqual({
      label: locale === "en" ? "Previous assessment revision" : "Revisi perhitungan sebelumnya",
      value: "3",
    });
  });
  it.each(["en", "id"] as const)(
    "keeps refunds below one rupiah and large decimals exact in %s",
    (locale) => {
      expect(payrollAmountView("-0.50", locale)).toContain("-");
      expect(payrollAmountView("0.50", locale)).not.toContain("-");
      expect(payrollAmountView("60000000000.25", locale)).toContain(
        locale === "id" ? "60.000.000.000,25" : "60,000,000,000.25",
      );
    },
  );
  it("scopes result routes and retains the parent result page", () => {
    expect(payrollRunPath("company", "run", "50", "attempts")).toBe(
      "/payroll/runs/run?company=company&tab=attempts&after=50",
    );
    expect(payrollRunEmployeePath("company", "run", "employee", "50")).toBe(
      "/payroll/runs/run/employees/employee?company=company&after=50",
    );
  });
});
