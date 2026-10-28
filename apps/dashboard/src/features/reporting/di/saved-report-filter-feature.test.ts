import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { type ReportKind, reportDefinitions } from "../domain/entities/report-catalogue";
import type {
  ReportFilterParameters,
  SaveReportFilterChange,
} from "../domain/entities/saved-report-filter";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const foreign = "87ed1727-6b89-48f9-ae10-f5f3f87c93f1" as CompanyId;
const id = "00000000-0000-4000-8000-000000000001";
const operation = "00000000-0000-4000-8000-000000000002" as OperationId;
const access = {
  companyId,
  permissions: [
    "reports.read",
    "people.read",
    "workforce.read",
    "leave.read",
    "expenses.read",
    "payroll.read",
  ],
};
const empty: ReportFilterParameters = {
  asOf: null,
  from: null,
  until: null,
  status: null,
  companies: [],
};
const change: SaveReportFilterChange = {
  id,
  name: "My selection",
  kind: "HEADCOUNT",
  definitionVersion: "headcount.v1",
  parameters: { ...empty, asOf: "2026-10-01", companies: [companyId] },
  expectedVersion: null,
};
const dto = {
  id,
  companyId,
  name: change.name,
  kind: change.kind,
  definitionVersion: change.definitionVersion,
  parameters: change.parameters,
  version: 0,
  createdAt: "2026-10-01T00:00:00.000001Z",
  updatedAt: "2026-10-01T00:00:00.000002Z",
};
const signal = () => new AbortController().signal;
const rejected = (code: string) => ({ ok: false, failure: { code } });

describe("saved report filter boundary", () => {
  it("requires current report/source grants, while retaining metadata and cleanup after source loss", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const actions = createReportingFeature({ request });
    const denied = { companyId, permissions: ["people.read"] };
    expect(await actions.listSavedFilters.execute(denied, signal())).toMatchObject(
      rejected("access_denied"),
    );
    expect(await actions.getSavedFilter.execute(denied, id, signal())).toMatchObject(
      rejected("access_denied"),
    );
    expect(await actions.saveFilter.execute(denied, operation, change, signal())).toMatchObject(
      rejected("access_denied"),
    );
    expect(
      await actions.deleteFilter.execute(denied, operation, { id, expectedVersion: 0 }, signal()),
    ).toMatchObject(rejected("access_denied"));
    const reader = { companyId, permissions: ["reports.read", "people.team.read"] };
    expect(await actions.saveFilter.execute(reader, operation, change, signal())).toMatchObject(
      rejected("access_denied"),
    );
    expect(request).not.toHaveBeenCalled();
    request
      .mockResolvedValueOnce({ companyId, items: [dto] })
      .mockResolvedValueOnce(dto)
      .mockResolvedValueOnce({ id, version: 1 });
    expect(await actions.listSavedFilters.execute(reader, signal())).toMatchObject({
      ok: true,
      value: [dto],
    });
    expect(await actions.getSavedFilter.execute(reader, id, signal())).toMatchObject({
      ok: true,
      value: dto,
    });
    expect(
      await actions.deleteFilter.execute(reader, operation, { id, expectedVersion: 0 }, signal()),
    ).toEqual({ ok: true, value: { id, version: 1 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/reports/saved-filters/${id}/delete`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 0 },
    });
  });

  it.each(Object.keys(reportDefinitions) as ReportKind[])(
    "saves canonical %s selections and validates the observed receipt",
    async (kind) => {
      const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id, version: 5 });
      const actions = createReportingFeature({ request });
      const month = kind === "ATTENDANCE_CLOSINGS" || kind === "PAYROLL_COSTS";
      const parameters: ReportFilterParameters =
        kind === "HEADCOUNT"
          ? { ...empty, asOf: "2026-10-01", companies: [foreign] }
          : {
              ...empty,
              from: month ? "2026-01" : "2026-10-01",
              until: month ? "2026-12" : "2026-10-20",
              status: kind === "OVERTIME" ? "APPROVED" : null,
            };
      const input = {
        ...change,
        kind,
        definitionVersion: reportDefinitions[kind],
        parameters,
        expectedVersion: 4,
        name: "  October  ",
      };
      expect(await actions.saveFilter.execute(access, operation, input, signal())).toEqual({
        ok: true,
        value: { id, version: 5 },
      });
      expect(request.mock.lastCall?.[0]).toEqual({
        path: `/api/v1/companies/${companyId}/reports/saved-filters/${id}`,
        method: "PUT",
        operationId: operation,
        body: {
          name: "October",
          kind,
          definitionVersion: reportDefinitions[kind],
          parameters,
          expectedVersion: 4,
        },
      });
      request.mockResolvedValueOnce({
        ...dto,
        kind,
        definitionVersion: reportDefinitions[kind],
        parameters,
      });
      const loaded = await actions.getSavedFilter.execute(access, id, signal());
      expect(loaded.ok).toBe(true);
      if (!loaded.ok) throw new Error("Expected saved selection");
      expect(Object.isFrozen(loaded.value)).toBe(true);
      expect(Object.isFrozen(loaded.value.parameters)).toBe(true);
      expect(Object.isFrozen(loaded.value.parameters.companies)).toBe(true);
      expect(loaded.value.parameters).toEqual(parameters);
    },
  );

  it("rejects invalid selections and IDs before I/O and preserves the selected group on save", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id, version: 0 });
    const actions = createReportingFeature({ request });
    const invalid: Partial<SaveReportFilterChange>[] = [
      { name: "   " },
      { name: "a".repeat(81) },
      { name: "line\nname" },
      { definitionVersion: "headcount.v2" },
      { id: "../private" },
      { expectedVersion: -1 },
      { expectedVersion: 0.5 },
      { expectedVersion: 10001 },
      { parameters: { ...change.parameters, asOf: "2026-02-30" } },
      { parameters: { ...change.parameters, from: "2026-01-01" } },
      { parameters: { ...change.parameters, status: "APPROVED" } },
      { parameters: { ...change.parameters, companies: [foreign, foreign] } },
      { parameters: { ...change.parameters, companies: Array(33).fill(foreign) } },
    ];
    for (const fields of invalid)
      expect(
        (await actions.saveFilter.execute(access, operation, { ...change, ...fields }, signal()))
          .ok,
      ).toBe(false);
    for (const kind of [
      "ATTENDANCE_EXCEPTIONS",
      "ATTENDANCE_CLOSINGS",
      "LEAVE",
      "OVERTIME",
      "EXPENSES",
      "PAYROLL_COSTS",
    ] as const) {
      for (const parameters of [
        { ...empty, from: "2026-01-01", until: "2027-01-02" },
        { ...empty, from: "2026-10", until: "2026-01" },
        { ...empty, from: "2026-10-01", until: "2026-10-20", status: "NOT_A_STATUS" },
        { ...empty, from: "2026-10-01", until: "2026-10-20", companies: [companyId] },
      ])
        expect(
          (
            await actions.saveFilter.execute(
              access,
              operation,
              { ...change, kind, definitionVersion: reportDefinitions[kind], parameters },
              signal(),
            )
          ).ok,
        ).toBe(false);
    }
    expect(request).not.toHaveBeenCalled();
    await actions.saveFilter.execute(
      access,
      operation,
      { ...change, parameters: { ...change.parameters, companies: [foreign] } },
      signal(),
    );
    expect(request.mock.lastCall?.[0].body).toMatchObject({ parameters: { companies: [foreign] } });
    await actions.saveFilter.execute(
      access,
      operation,
      { ...change, parameters: { ...change.parameters, companies: [] } },
      signal(),
    );
    expect(request.mock.lastCall?.[0].body).toMatchObject({
      parameters: { companies: [companyId] },
    });
  });

  it("rejects wrong partitions, definitions, noncanonical values, oversized lists and timestamp regressions", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const actions = createReportingFeature({ request });
    for (const fields of [
      { id: crypto.randomUUID() },
      { companyId: foreign },
      { accountId: crypto.randomUUID() },
      { name: " name " },
      { name: "bad\nname" },
      { kind: "__proto__" },
      { definitionVersion: "headcount.v2" },
      { version: 10000 },
      { updatedAt: "2026-10-01T00:00:00.000000Z" },
      { createdAt: "never" },
      { parameters: { ...change.parameters, companies: [] } },
      { parameters: { ...change.parameters, companies: [foreign, foreign.toUpperCase()] } },
      { parameters: { ...change.parameters, url: "https://private.example" } },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...fields });
      expect(await actions.getSavedFilter.execute(access, id, signal())).toMatchObject(
        rejected("invalid_response"),
      );
    }
    for (const list of [
      { companyId: foreign, items: [] },
      { companyId, items: [dto, dto] },
      { companyId, items: Array(51).fill(dto) },
      { companyId, items: [{ ...dto, companyId: foreign }] },
    ]) {
      request.mockResolvedValueOnce(list);
      expect(await actions.listSavedFilters.execute(access, signal())).toMatchObject(
        rejected("invalid_response"),
      );
    }
  });

  it("requires matching save and deletion receipts, including erased identity recovery", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const actions = createReportingFeature({ request });
    for (const receipt of [
      { id: foreign, version: 0 },
      { id, version: 1 },
      { id, version: -1 },
      { id, version: 0.1 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await actions.saveFilter.execute(access, operation, change, signal())).toMatchObject(
        rejected("invalid_response"),
      );
    }
    for (const version of [1, 2]) {
      request.mockResolvedValueOnce({ id, version });
      expect(
        await actions.deleteFilter.execute(access, operation, { id, expectedVersion: 1 }, signal()),
      ).toEqual({ ok: true, value: { id, version } });
    }
    for (const version of [0, 3]) {
      request.mockResolvedValueOnce({ id, version });
      expect(
        await actions.deleteFilter.execute(access, operation, { id, expectedVersion: 1 }, signal()),
      ).toMatchObject(rejected("invalid_response"));
    }
  });

  it("detaches command inputs before waiting, preserves cancellation and sanitizes technical failures without retry", async () => {
    let resolve!: (value: unknown) => void;
    const request = vi.fn<HttpClient["request"]>().mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const actions = createReportingFeature({ request });
    const selected = [foreign];
    const pending = actions.saveFilter.execute(
      access,
      operation,
      { ...change, parameters: { ...change.parameters, companies: selected } },
      signal(),
    );
    selected.push(companyId);
    expect(request.mock.lastCall?.[0].body).toMatchObject({ parameters: { companies: [foreign] } });
    resolve({ id, version: 0 });
    expect((await pending).ok).toBe(true);
    const aborted = new AbortController();
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const late = actions.getSavedFilter.execute(access, id, aborted.signal);
    aborted.abort();
    resolve(dto);
    await expect(late).rejects.toThrow();
    request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
    );
    const denied = await actions.listSavedFilters.execute(access, signal());
    expect(denied).toMatchObject(rejected("mfa_required"));
    expect(JSON.stringify(denied)).not.toContain("PRIVATE");
    request.mockRejectedValueOnce(new TypeError("PRIVATE"));
    expect(await actions.saveFilter.execute(access, operation, change, signal())).toMatchObject(
      rejected("connection_unavailable"),
    );
    expect(request).toHaveBeenCalledTimes(4);
  });
});
