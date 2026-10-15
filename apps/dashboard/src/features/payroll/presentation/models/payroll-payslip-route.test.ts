import { describe, expect, it } from "vitest";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId } from "../../../../../tests/fixtures/payroll-payslips";
import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  payrollPayslipRevisionFromRoute,
  payrollPayslipRevisionLinks,
} from "./payroll-payslip-route";

describe("payslip revision navigation", () => {
  it("distinguishes the current selection from retained revision zero", () => {
    expect(payrollPayslipRevisionFromRoute(new URLSearchParams())).toBeNull();
    expect(payrollPayslipRevisionFromRoute(new URLSearchParams("revision=0"))).toBe(0);
    expect(payrollPayslipRevisionFromRoute(new URLSearchParams("revision=9007199254740991"))).toBe(
      Number.MAX_SAFE_INTEGER,
    );
  });
  it.each([
    "",
    "-1",
    "01",
    "1.0",
    "1e2",
    "Infinity",
    "NaN",
    " 1",
    "9007199254740992",
    "99999999999999999",
    "1/other",
  ])("keeps malformed selection %s invalid for application validation", (revision) => {
    expect(payrollPayslipRevisionFromRoute(new URLSearchParams({ revision }))).toBeNaN();
  });
  it("rejects ambiguous repeated selections", () => {
    expect(payrollPayslipRevisionFromRoute(new URLSearchParams("revision=0&revision=1"))).toBeNaN();
  });
  const input = {
    id: payslipId(),
    company: calculationCompany as CompanyId,
    scope: "SELF" as const,
    requested: 1,
    viewed: 1,
    current: 3,
  };
  it("preserves search, payment tab and scope while selecting one retained version", () => {
    const parameters = new URLSearchParams(
      "company=old&scope=AUTHORIZED&revision=1&tab=payments&from=2026-01&until=2026-12&query=Example",
    );
    const links = payrollPayslipRevisionLinks(parameters, input);
    const previous = new URL(links.previousTo ?? "", "https://example.invalid");
    expect(previous.pathname).toBe(`/payroll/payslips/${payslipId()}`);
    expect(Object.fromEntries(previous.searchParams)).toEqual({
      company: calculationCompany,
      scope: "SELF",
      revision: "0",
      tab: "payments",
      from: "2026-01",
      until: "2026-12",
      query: "Example",
    });
    expect(links.nextTo).toContain("revision=2");
    expect(links.latestTo).not.toContain("revision=");
    expect(links.originalTo).toBeNull();
    expect(links.retained).toBe(true);
    expect(links.newer).toBe(true);
    expect(parameters.get("company")).toBe("old");
  });
  it("has no preceding version for the original and no invented next version without current evidence", () => {
    const links = payrollPayslipRevisionLinks(new URLSearchParams(), {
      ...input,
      requested: 0,
      viewed: 0,
      current: null,
    });
    expect(links.previousTo).toBeNull();
    expect(links.nextTo).toBeNull();
    expect(links.latestTo).not.toBeNull();
  });
  it("offers the latest path after an invalid selection without synthesizing a version", () => {
    const links = payrollPayslipRevisionLinks(new URLSearchParams("revision=bad"), {
      ...input,
      requested: Number.NaN,
      viewed: null,
      current: null,
    });
    expect(links.latestTo).not.toContain("revision");
    expect(links.previousTo).toBeNull();
    expect(links.nextTo).toBeNull();
    expect(links.retained).toBe(false);
  });
  it("uses bounded links at the largest representable revision", () => {
    const links = payrollPayslipRevisionLinks(new URLSearchParams(), {
      ...input,
      requested: null,
      viewed: Number.MAX_SAFE_INTEGER,
      current: Number.MAX_SAFE_INTEGER,
    });
    expect(links.previousTo).toContain(`revision=${Number.MAX_SAFE_INTEGER - 1}`);
    expect(links.originalTo).toContain("revision=0");
    expect(links.nextTo).toBeNull();
    expect(links.latestTo).toBeNull();
  });
  it("never builds resource links from an invalid route identifier", () => {
    const links = payrollPayslipRevisionLinks(new URLSearchParams(), {
      ...input,
      id: "../../outside",
      viewed: 2,
    });
    expect([links.previousTo, links.nextTo, links.originalTo, links.latestTo]).toEqual([
      null,
      null,
      null,
      null,
    ]);
  });
});
