import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createPeopleFeature } from "./people-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "30000000-0000-4000-8000-000000000001";
const job = "40000000-0000-4000-8000-000000000001";
const actor = "20000000-0000-4000-8000-000000000001";
const permissions = [
  "people.import",
  "people.manage",
  "people.profile.read",
  "people.profile.manage",
];
const access = { companyId: company, permissions };
const jobContext = {
  jobStatus: "SUCCEEDED",
  cancellationRequested: false,
  availableActions: ["apply", "cancel"],
};
const signal = () => new AbortController().signal;
const batch = {
  id,
  fileName: "employees.csv",
  sourceHash: "a".repeat(64),
  rowCount: 2,
  status: "REVIEW",
  jobId: job,
  version: 1,
  createdBy: actor,
  createdAt: "2026-10-01T00:00:00Z",
  reason: "Intake",
};
const row = {
  number: 1,
  employeeNumber: "E01",
  legalName: "Review Employee",
  status: "INVALID",
  issues: { terms: "employment_date_outside_import_range" },
  proposed: {
    employeeId: id,
    employeeNumber: "E01",
    legalName: "Review Employee",
    nationality: "ID",
    birthDate: null,
    email: null,
    terms: {
      effectiveFrom: "+10000-01-01",
      startDate: "+10000-01-01",
      endDate: "2025-01-01",
      status: "ACTIVE",
      contract: "FIXED_TERM",
      branchId: null,
      departmentId: null,
      positionId: null,
      costCenterId: null,
      managerId: null,
    },
  },
  createdEmploymentId: null,
};

describe("employee import read boundary", () => {
  it("requires every import grant before acquiring summaries or private row proposals", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const missing of permissions) {
      const denied = {
        companyId: company,
        permissions: permissions.filter((permission) => permission !== missing),
      };
      for (const result of await Promise.all([
        feature.loadEmployeeImports.execute(denied, null, signal()),
        feature.loadEmployeeImport.execute(denied, id, signal()),
        feature.loadEmployeeImportRows.execute(denied, id, null, signal()),
        feature.loadEmployeeImportAttempts.execute(denied, id, null, signal()),
      ]))
        expect(result).toMatchObject({
          ok: false,
          failure: { code: "employee_import_access_required" },
        });
    }
    expect(request).not.toHaveBeenCalled();
  });
  it("rejects malformed identifiers and finite cursors before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const after of ["../imports", "", "null"])
      expect(await feature.loadEmployeeImports.execute(access, after, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    for (const after of ["-1", "1e3", "01", "5001", "99999999999", ""])
      expect(
        await feature.loadEmployeeImportRows.execute(access, id, after, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(await feature.loadEmployeeImport.execute(access, "../imports", signal())).toMatchObject({
      ok: false,
    });
    expect(
      await feature.loadEmployeeImportAttempts.execute(access, id, "1", signal()),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });
  it("maps immutable scoped headers, count snapshots, and ascending attempt pages", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ items: [batch], nextCursor: null })
      .mockResolvedValueOnce({ batch, counts: { READY: 1, INVALID: 1 }, ...jobContext })
      .mockResolvedValueOnce({
        items: [{ jobId: job, phase: "PREVIEW", actorId: actor, createdAt: batch.createdAt }],
        nextCursor: null,
      });
    const feature = createPeopleFeature({ request });
    const page = await feature.loadEmployeeImports.execute(access, null, signal());
    expect(page).toMatchObject({ ok: true, value: { items: [{ id, companyId: company }] } });
    const summary = await feature.loadEmployeeImport.execute(access, id.toUpperCase(), signal());
    expect(summary).toMatchObject({
      ok: true,
      value: {
        batch: { id, companyId: company },
        counts: { PENDING: 0, READY: 1, INVALID: 1, APPLIED: 0, REJECTED: 0 },
      },
    });
    if (!summary.ok) throw new Error("Expected summary");
    expect(Object.isFrozen(summary.value.batch)).toBe(true);
    expect(Object.isFrozen(summary.value.counts)).toBe(true);
    expect(Object.isFrozen(summary.value.availableActions)).toBe(true);
    expect(summary.value.availableActions).toEqual(["apply", "cancel"]);
    expect(
      await feature.loadEmployeeImportAttempts.execute(access, id, null, signal()),
    ).toMatchObject({ ok: true });
    expect(request.mock.calls.map(([call]) => call.path)).toEqual([
      `/api/v1/companies/${company}/employee-imports?limit=10`,
      `/api/v1/companies/${company}/employee-imports/${id}`,
      `/api/v1/companies/${company}/employee-imports/${id}/attempts?limit=10`,
    ]);
  });
  it("retains invalid proposals without reapplying employment rules in the mapper", async () => {
    const source = structuredClone(row);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      items: [source, { ...row, number: 2, proposed: null, issues: { startDate: "invalid_date" } }],
      nextCursor: null,
    });
    const result = await createPeopleFeature({ request }).loadEmployeeImportRows.execute(
      access,
      id,
      "0",
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        items: [
          { proposed: { terms: { startDate: "+10000-01-01", endDate: "2025-01-01" } } },
          { proposed: null },
        ],
      },
    });
    if (!result.ok) throw new Error("Expected invalid proposals");
    source.proposed.terms.startDate = "2026-01-01";
    source.issues.terms = "CHANGED";
    expect(result.value.items[0]?.proposed?.terms.startDate).toBe("+10000-01-01");
    expect(result.value.items[0]?.issues.terms).toBe("employment_date_outside_import_range");
    expect(Object.isFrozen(result.value.items[0]?.proposed?.terms)).toBe(true);
    expect(request.mock.lastCall?.[0].path).toContain("rows?limit=25&after=0");
  });
  it("rejects mismatched headers/counts, duplicate/reversed pages, oversized fields and contradictory outcomes", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const invalid of [
      { batch: { ...batch, id: job }, counts: { READY: 2 } },
      { batch, counts: { READY: 3 } },
      { batch: { ...batch, version: Number.MAX_SAFE_INTEGER + 1 }, counts: { READY: 2 } },
      { batch, counts: { READY: 2 }, jobStatus: "UNKNOWN" },
      { batch, counts: { READY: 2 }, availableActions: ["apply", "apply"] },
    ]) {
      request.mockResolvedValueOnce({ ...jobContext, ...invalid });
      expect(await feature.loadEmployeeImport.execute(access, id, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    for (const invalid of [
      { items: [row, row], nextCursor: null },
      { items: [{ ...row, number: 2 }, row], nextCursor: null },
      { items: [row], nextCursor: "1" },
      { items: [{ ...row, employeeNumber: "x".repeat(1025) }], nextCursor: null },
      { items: [{ ...row, status: "READY", proposed: null }], nextCursor: null },
      { items: [{ ...row, status: "APPLIED", createdEmploymentId: job }], nextCursor: null },
      {
        items: [{ ...row, proposed: { ...row.proposed, legalName: "Other person" } }],
        nextCursor: null,
      },
    ]) {
      request.mockResolvedValueOnce(invalid);
      expect(
        await feature.loadEmployeeImportRows.execute(access, id, null, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValueOnce({ items: [batch, batch], nextCursor: null });
    expect(await feature.loadEmployeeImports.execute(access, null, signal())).toMatchObject({
      ok: false,
    });
    request.mockResolvedValueOnce({
      items: [{ jobId: job, phase: "PREVIEW", actorId: actor, createdAt: "invalid" }],
      nextCursor: null,
    });
    expect(
      await feature.loadEmployeeImportAttempts.execute(access, id, null, signal()),
    ).toMatchObject({ ok: false });
  });
  it("preserves safe failures and cancellation without exposing technical text", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "employee_import_access_required",
        detail: "PRIVATE CSV",
        fields: {},
        parameters: {},
      }),
    );
    const load = createPeopleFeature({ request }).loadEmployeeImport;
    const result = await load.execute(access, id, signal());
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "employee_import_access_required" },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => load.execute(access, id, cancelled.signal)).toThrow();
    request.mockRejectedValue(new DOMException("cancelled", "AbortError"));
    await expect(load.execute(access, id, signal())).rejects.toMatchObject({ name: "AbortError" });
  });
});
