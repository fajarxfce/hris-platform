import { describe, expect, it } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  payrollAmendmentParameters,
  payrollAmendmentSearchFromRoute,
  payrollAmendmentSelectionFromRoute,
} from "./payroll-amendment-route";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
describe("amendment route ownership", () => {
  it.each(["revision=wrong", "revision=-1", "revision=1000", "revision=0&revision=1", "revision="])(
    "keeps %s invalid rather than selecting current",
    (query) => {
      expect(payrollAmendmentSelectionFromRoute(new URLSearchParams(query)).revision).toBeNaN();
    },
  );
  it("retains revision zero independently of the history page", () => {
    expect(
      payrollAmendmentSelectionFromRoute(new URLSearchParams("revision=0&historyAfter=24")),
    ).toEqual({ revision: 0, after: 24 });
  });
  it("company replacement drops another company's cursor and filters", () => {
    expect(
      payrollAmendmentSearchFromRoute(
        new URLSearchParams("company=other&year=2025&query=private&status=APPROVED&after=private"),
        company,
        2026,
      ),
    ).toEqual({ year: 2026, query: "", status: null, after: null, employeeId: null });
  });
  it("preserves literal filters while rejecting duplicate year selection", () => {
    const search = payrollAmendmentSearchFromRoute(
      new URLSearchParams("year=2026&query=A%25_B&status=DRAFT"),
      company,
      2025,
    );
    expect(payrollAmendmentParameters(company, search).get("query")).toBe("A%_B");
    expect(
      payrollAmendmentSearchFromRoute(new URLSearchParams("year=2026&year=2025"), company, 2026)
        .year,
    ).toBeNaN();
  });
});
