import { describe, expect, it } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { type ReportKind, reportDefinitions } from "../../domain/entities/report-catalogue";
import type { ReportFilterParameters } from "../../domain/entities/saved-report-filter";
import {
  openSavedReportFilter,
  reportFilterSeed,
  saveAppliedReportFilter,
} from "./saved-report-filter-route";

const companyId = "00000000-0000-4000-8000-000000000001" as CompanyId;
const foreign = "00000000-0000-4000-8000-000000000002" as CompanyId;
const access = { companyId, permissions: ["reports.read", "people.read"] };
describe("saved selection navigation", () => {
  it.each(Object.keys(reportDefinitions) as ReportKind[])(
    "retains only the main %s selection and anchor when opening and creating",
    (kind) => {
      const month = kind === "ATTENDANCE_CLOSINGS" || kind === "PAYROLL_COSTS";
      const parameters: ReportFilterParameters = {
        asOf: kind === "HEADCOUNT" ? "2026-10-01" : null,
        from: kind === "HEADCOUNT" ? null : month ? "2026-01" : "2026-10-01",
        until: kind === "HEADCOUNT" ? null : month ? "2026-12" : "2026-10-20",
        status: kind === "OVERTIME" ? "APPROVED" : null,
        companies: kind === "HEADCOUNT" ? [foreign] : [],
      };
      const path = saveAppliedReportFilter(companyId, kind, parameters);
      expect(path).not.toBeNull();
      const query = new URL(path ?? "", "https://example.invalid").searchParams;
      const seed = reportFilterSeed(access, "2026-11-01", query);
      expect(seed).toEqual({
        name: "",
        kind,
        definitionVersion: reportDefinitions[kind],
        parameters,
      });
      if (!seed) throw new Error("Expected seed");
      const opened = new URL(
        openSavedReportFilter({
          ...seed,
          name: "PRIVATE NAME",
          id: foreign,
          companyId,
          version: 1,
          createdAt: "2026-10-01T00:00:00Z",
          updatedAt: "2026-10-01T00:00:00Z",
        }),
        "https://example.invalid",
      );
      expect(opened.searchParams.get("company")).toBe(companyId);
      expect(opened.href).not.toContain("PRIVATE");
      expect(opened.href).not.toContain("saved-filters");
      if (kind === "HEADCOUNT") expect(opened.searchParams.get("companies")).toBe(foreign);
    },
  );
  it("refuses invalid or ambiguous selections without silently replacing dates or companies", () => {
    for (const query of [
      "kind=__proto__",
      "kind=HEADCOUNT&asOf=2026-02-30",
      "kind=HEADCOUNT&companies=",
      `kind=HEADCOUNT&companies=${Array(33).fill(foreign).join(",")}`,
      "kind=LEAVE&from=2026-10-01&until=2026-10-20&status=APPROVED",
      "kind=HEADCOUNT&asOf=2026-10-01&asOf=2026-10-02",
      `name=${"a".repeat(2501)}`,
    ]) {
      expect(reportFilterSeed(access, "2026-10-01", new URLSearchParams(query))).toBeNull();
    }
    expect(
      saveAppliedReportFilter(companyId, "HEADCOUNT", {
        asOf: "bad",
        from: null,
        until: null,
        status: null,
        companies: [companyId],
      }),
    ).toBeNull();
  });
});
