import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { reportDefinitions } from "../domain/entities/report-catalogue";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const permissions = [
  "reports.read",
  "people.read",
  "workforce.read",
  "leave.read",
  "expenses.read",
  "payroll.read",
];
const access = { companyId, permissions };
const dto = {
  companyId,
  evaluatedAt: "2026-10-01T00:00:00Z",
  reports: Object.entries(reportDefinitions).map(([kind, definitionVersion]) => ({
    kind,
    definitionVersion,
  })),
};
const signal = () => new AbortController().signal;

describe("report catalogue boundary", () => {
  it("requires reporting access before I/O and intersects returned sources with the initiating company grants", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature({ request }).loadCatalogue;
    expect(await load.execute({ companyId, permissions: ["people.read"] }, signal())).toMatchObject(
      { ok: false, failure: { code: "access_denied" } },
    );
    expect(request).not.toHaveBeenCalled();
    const restricted = await load.execute(
      { companyId, permissions: ["reports.read", "people.team.read", "expenses.team.read"] },
      signal(),
    );
    expect(restricted).toMatchObject({ ok: true, value: { reports: [] } });
    const partial = await load.execute(
      { companyId, permissions: ["reports.read", "people.read"] },
      signal(),
    );
    expect(partial).toMatchObject({ ok: true, value: { reports: [{ kind: "HEADCOUNT" }] } });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/reports/catalogue`,
    );
  });
  it("bounds and validates response identities, definitions and company before exposing detached immutable metadata", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature({ request }).loadCatalogue;
    const result = await load.execute(access, signal());
    expect(result).toEqual({ ok: true, value: dto });
    if (!result.ok) throw new Error("Expected catalogue");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.reports)).toBe(true);
    expect(Object.isFrozen(result.value.reports[0])).toBe(true);
    for (const change of [
      { companyId: crypto.randomUUID() },
      { evaluatedAt: "bad" },
      { reports: Array(8).fill(dto.reports[0]) },
      { reports: [dto.reports[0], dto.reports[0]] },
      { reports: [{ kind: "__proto__", definitionVersion: "v1" }] },
      { reports: [{ kind: "HEADCOUNT", definitionVersion: "headcount.v2" }] },
      { reports: [{ kind: "UNKNOWN", definitionVersion: "unknown.v1" }] },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...change });
      expect(await load.execute(access, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("captures permissions before waiting, rejects cancellation and sanitizes failures without retry", async () => {
    let resolve!: (value: unknown) => void;
    const request = vi.fn<HttpClient["request"]>().mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const load = createReportingFeature({ request }).loadCatalogue;
    const selected = ["reports.read", "people.read"];
    const pending = load.execute({ companyId, permissions: selected }, signal());
    selected.push("payroll.read");
    resolve(dto);
    expect(await pending).toMatchObject({ ok: true, value: { reports: [{ kind: "HEADCOUNT" }] } });
    request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "access_denied", detail: "PRIVATE DIAGNOSTIC" }),
    );
    const denied = await load.execute(access, signal());
    expect(denied).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(JSON.stringify(denied)).not.toContain("PRIVATE");
    const aborted = new AbortController();
    aborted.abort();
    await expect(load.execute(access, aborted.signal)).rejects.toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
