import { expect, it } from "vitest";
import { defaultAuditSearch } from "../../domain/entities/audit-search";
import { auditFiltersFromSearch, auditSearchFromFilters } from "./audit-filter-values";
import { auditSearchFromParameters, auditSearchParameters } from "./audit-route";

it("preserves exact server windows in URL continuation while form edits use explicit UTC seconds", () => {
  const company = "10000000-0000-4000-8000-000000000001";
  const query = {
    ...defaultAuditSearch,
    from: "2026-09-01T00:00:00.000123Z",
    until: "2026-10-01T00:00:00.000123Z",
    cursor: "40000000-0000-4000-8000-000000000001",
    action: "employment.created",
  };
  const parameters = auditSearchParameters(query, company);
  expect(auditSearchFromParameters(parameters, company)).toEqual(query);
  expect(auditSearchFromParameters(parameters, "other-company")).toEqual({
    ...query,
    cursor: null,
  });
  const form = auditFiltersFromSearch(query);
  expect(form.from).toBe("2026-09-01T00:00:00");
  expect(form.until).toBe("2026-10-01T00:00:00");
  expect(
    auditSearchFromFilters({ ...form, from: "2026-09-02T14:25", action: " employment.created " }),
  ).toEqual({
    ...query,
    from: "2026-09-02T14:25:00Z",
    until: "2026-10-01T00:00:00Z",
    cursor: null,
  });
});
