import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { EmployeeDto } from "../data/models/employee-dto";
import type { EmploymentRevisionDto } from "../data/models/employment-revision-dto";
import { createPeopleFeature } from "./people-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["people.read"] };
const employeeId = (value = 1) => `40000000-abcd-4000-8000-${String(value).padStart(12, "0")}`;
const search = { asOf: "2026-10-01", query: "", after: null };
const signal = () => new AbortController().signal;
const employee = (value = 1): EmployeeDto => ({
  id: employeeId(value),
  companyId,
  employeeNumber: `EMP-${String(value).padStart(3, "0")}`,
  person: { legalName: `Fixture employee ${value}`, email: "employee@internal" },
  terms: {
    effectiveFrom: "2026-01-01",
    startDate: "2026-01-01",
    endDate: null,
    status: "ACTIVE",
    contract: "PERMANENT",
  },
  version: 3,
  appliedRevision: 0,
});
const revision = (value = 0): EmploymentRevisionDto => ({
  revision: value,
  terms: employee().terms,
  reason: `Revision ${value}`,
  recordedAt: "2026-01-01T00:00:00.000123Z",
  cancellation: null,
});

describe("people feature contracts", () => {
  it("validates permissions, dates, identities, query bounds and cursors before I/O", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items: [], nextCursor: null });
    const feature = createPeopleFeature({ request });
    for (const value of [
      { ...search, asOf: "2026-02-30" },
      { ...search, query: "a".repeat(121) },
      { ...search, after: "../another" },
      { ...search, after: "" },
    ])
      expect(await feature.loadEmployees.execute(access, value, signal())).toMatchObject({
        ok: false,
      });
    for (const value of ["-1", "01", "1e2", "9007199254740992", ""]) {
      expect(
        await feature.loadEmploymentHistory.execute(access, employeeId(), value, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    }
    expect(
      await feature.loadEmployee.execute(access, "../another", search.asOf, signal()),
    ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
    expect(
      await feature.loadEmployee.execute(access, employeeId(), "0000-01-01", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadEmployees.execute({ ...access, permissions: [] }, search, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const permission of [
      "people.team.read",
      "people.self.read",
      "people.manage",
      "people.profile.read",
    ])
      expect(
        await feature.loadEmploymentHistory.execute(
          { ...access, permissions: [permission] },
          employeeId(),
          null,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(request).not.toHaveBeenCalled();
    for (const permission of ["people.read", "people.team.read", "people.self.read"])
      expect(
        await feature.loadEmployees.execute(
          { ...access, permissions: [permission] },
          search,
          signal(),
        ),
      ).toMatchObject({ ok: true });
    expect(request).toHaveBeenCalledTimes(3);
  });

  it("uses bounded encoded requests and maps an immutable public projection", async () => {
    const dto = {
      ...employee(),
      id: employeeId().toUpperCase(),
      person: {
        ...employee().person,
        birthDate: "PRIVATE",
        nationality: "PRIVATE",
        accountId: "PRIVATE",
      },
      terms: { ...employee().terms, managerId: "PRIVATE" },
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ items: [dto], nextCursor: null })
      .mockResolvedValueOnce(dto);
    const feature = createPeopleFeature({ request });
    const response = await feature.loadEmployees.execute(
      access,
      { ...search, query: "  A&B +_%  ", after: "EMP-000" },
      signal(),
    );
    if (!response.ok) throw new Error("Directory fixture was rejected");
    expect(
      Object.fromEntries(
        new URL(request.mock.calls[0]?.[0].path ?? "", "https://fixture.invalid").searchParams,
      ),
    ).toEqual({ asOf: search.asOf, query: "A&B +_%", limit: "50", after: "EMP-000" });
    const mapped = response.value.items[0];
    expect(mapped).toMatchObject({
      id: employeeId(),
      email: "employee@internal",
      version: 3,
      appliedRevision: 0,
    });
    expect(Object.isFrozen(response.value)).toBe(true);
    expect(Object.isFrozen(response.value.items)).toBe(true);
    expect(Object.isFrozen(mapped)).toBe(true);
    expect(Object.isFrozen(mapped?.terms)).toBe(true);
    expect(JSON.stringify(response)).not.toContain("PRIVATE");
    expect(
      await feature.loadEmployee.execute(access, employeeId().toUpperCase(), search.asOf, signal()),
    ).toMatchObject({ ok: true, value: mapped });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/employees/${employeeId()}?asOf=2026-10-01`,
    );
  });

  it("rejects another company's or employee's response and inconsistent employment records", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    for (const dto of [
      { ...employee(), companyId: employeeId(8) },
      { ...employee(), id: employeeId(8) },
      { ...employee(), employeeNumber: "bad number" },
      { ...employee(), person: { legalName: " ", email: null } },
      { ...employee(), appliedRevision: 4 },
      { ...employee(), version: Number.MAX_SAFE_INTEGER + 1 },
      { ...employee(), terms: { ...employee().terms, effectiveFrom: "2026-11-01" } },
      { ...employee(), terms: { ...employee().terms, startDate: "2026-04-01" } },
      { ...employee(), terms: { ...employee().terms, endDate: "2025-12-31" } },
      { ...employee(), terms: { ...employee().terms, contract: "FIXED_TERM" } },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(
        await feature.loadEmployee.execute(access, employeeId(), search.asOf, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("bounds directory pages and rejects duplicate or stalled continuations without assuming SQL collation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    const items = Array.from({ length: 50 }, (_, index) => employee(index + 1));
    request.mockResolvedValueOnce({ items, nextCursor: "EMP-050" });
    expect(await feature.loadEmployees.execute(access, search, signal())).toMatchObject({
      ok: true,
      value: { nextCursor: "EMP-050" },
    });
    for (const page of [
      { items: [employee(), employee()], nextCursor: null },
      {
        items: [employee(), { ...employee(2), employeeNumber: employee().employeeNumber }],
        nextCursor: null,
      },
      { items: [employee()], nextCursor: "EMP-001" },
      { items, nextCursor: "EMP-049" },
      { items: [...items, employee(51)], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(await feature.loadEmployees.execute(access, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ items: [employee(50)], nextCursor: null });
    expect(
      await feature.loadEmployees.execute(access, { ...search, after: "EMP-050" }, signal()),
    ).toMatchObject({ ok: false });
    request.mockResolvedValueOnce({
      items: [
        { ...employee(1), employeeNumber: "EMP_B" },
        { ...employee(2), employeeNumber: "EMP-A" },
      ],
      nextCursor: null,
    });
    expect(await feature.loadEmployees.execute(access, search, signal())).toMatchObject({
      ok: true,
    });
  });

  it("retains immutable cancellation evidence and numeric revision cursors, including revision zero", async () => {
    const cancelled = {
      ...revision(2),
      terms: { ...employee().terms, effectiveFrom: "2027-01-01" },
      cancellation: {
        reason: "Schedule changed",
        recordedAt: "2026-02-01T00:00:00Z",
        actorId: "PRIVATE",
      },
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ items: [cancelled, revision()], nextCursor: null })
      .mockResolvedValueOnce({ items: [revision()], nextCursor: null });
    const feature = createPeopleFeature({ request });
    const result = await feature.loadEmploymentHistory.execute(
      access,
      employeeId(),
      null,
      signal(),
    );
    if (!result.ok) throw new Error("History fixture was rejected");
    expect(result.value.items.map((item) => item.revision)).toEqual([2, 0]);
    expect(Object.isFrozen(result.value.items[0]?.cancellation)).toBe(true);
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(
      await feature.loadEmploymentHistory.execute(access, employeeId(), "2", signal()),
    ).toMatchObject({ ok: true });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/employees/${employeeId()}/history?limit=50&after=2`,
    );
    request.mockResolvedValueOnce({
      items: Array.from({ length: 50 }, (_, index) => revision(49 - index)),
      nextCursor: "0",
    });
    expect(
      await feature.loadEmploymentHistory.execute(access, employeeId(), null, signal()),
    ).toMatchObject({ ok: true, value: { nextCursor: "0" } });
    for (const page of [
      { items: [revision(2), revision(3)], nextCursor: null },
      { items: [revision(2), revision(2)], nextCursor: null },
      { items: [revision(2)], nextCursor: "2" },
      { items: [revision(4)], nextCursor: null },
      { items: [{ ...revision(2), revision: Number.MAX_SAFE_INTEGER + 1 }], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(
        await feature.loadEmploymentHistory.execute(access, employeeId(), "4", signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValueOnce({ items: [], nextCursor: null });
    expect(
      await feature.loadEmploymentHistory.execute(access, employeeId(), "0", signal()),
    ).toEqual({ ok: true, value: { items: [], nextCursor: null } });
  });

  it("preserves localizable failures, propagates cancellation and never retries", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "company_access_denied",
        detail: "PRIVATE SQL DETAIL",
        correlationId: employeeId(),
      }),
    );
    const feature = createPeopleFeature({ request });
    const failed = await feature.loadEmployees.execute(access, search, signal());
    expect(failed).toMatchObject({
      ok: false,
      failure: { code: "company_access_denied", correlationId: employeeId() },
    });
    expect(JSON.stringify(failed)).not.toContain("PRIVATE");
    const controller = new AbortController();
    let resolve!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = feature.loadEmployee.execute(
      access,
      employeeId(),
      search.asOf,
      controller.signal,
    );
    controller.abort();
    resolve(employee());
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    expect(() => feature.loadEmployees.execute(access, search, controller.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
