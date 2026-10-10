import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { OrganizationUnitDto } from "../data/models/organization-unit-dto";
import type { OrganizationUnitChange } from "../domain/entities/organization-unit-change";
import { createOrganizationFeature } from "./organization-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["company.read"] };
const id = (value = 1) => `50000000-abcd-4000-8000-${String(value).padStart(12, "0")}`;
const search = { query: "", kind: null, active: null, after: null };
const signal = () => new AbortController().signal;
const unit = (
  value = 1,
  kind: OrganizationUnitDto["kind"] = "DEPARTMENT",
): OrganizationUnitDto => ({
  id: id(value),
  code: `UNIT-${String(value).padStart(3, "0")}`,
  name: `Unit ${value}`,
  kind,
  parentId: null,
  timezone: kind === "BRANCH" ? "Asia/Jakarta" : null,
  active: true,
  version: 3,
});
const details = () => ({
  companyId,
  unit: { ...unit(), parentId: id(2) },
  parent: unit(2, "BRANCH"),
});

describe("organization feature boundary", () => {
  it("normalizes commands before I/O and sends only the DTO, observed version and operation key", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id().toUpperCase(), version: 0 });
    const feature = createOrganizationFeature({ request });
    const operation = id(99) as OperationId;
    const change: OrganizationUnitChange = {
      id: id().toUpperCase(),
      code: " sales_n ",
      name: " North sales ",
      kind: "DEPARTMENT",
      parentId: id(2).toUpperCase(),
      timezone: null,
      active: true,
      expectedVersion: null,
    };
    for (const permissions of [[], ["company.read"], ["company.manage"]])
      expect(
        await feature.saveUnit.execute({ ...access, permissions }, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    const grants = { ...access, permissions: ["company.read", "company.manage"] };
    for (const invalid of [
      { ...change, id: "../escape" },
      { ...change, code: "A" },
      { ...change, name: " " },
      { ...change, expectedVersion: -1 },
      { ...change, expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...change, timezone: "Asia/Jakarta" },
      { ...change, parentId: change.id },
    ])
      expect(await feature.saveUnit.execute(grants, operation, invalid, signal())).toMatchObject({
        ok: false,
      });
    expect(request).not.toHaveBeenCalled();
    const result = await feature.saveUnit.execute(grants, operation, change, signal());
    expect(result).toEqual({ ok: true, value: { id: id(), version: 0 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/organization-units/${id()}`,
      method: "PUT",
      operationId: operation,
      body: {
        code: "SALES_N",
        name: "North sales",
        kind: "DEPARTMENT",
        parentId: id(2),
        timezone: null,
        active: true,
        expectedVersion: null,
      },
    });
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
  });
  it("does not accept an unrelated or mismatched receipt and preserves command failures and cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createOrganizationFeature({ request });
    const change: OrganizationUnitChange = {
      id: id(),
      code: "SALES",
      name: "Sales",
      kind: "DEPARTMENT",
      parentId: null,
      timezone: null,
      active: false,
      expectedVersion: 7,
    };
    const grants = { ...access, permissions: ["company.read", "company.manage"] };
    for (const receipt of [
      { id: id(2), version: 8 },
      { id: id(), version: 7 },
      { id: id(), version: 9 },
      { id: id(), version: -1 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.saveUnit.execute(grants, id(99) as OperationId, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "stale_version",
        correlationId: id(9),
        detail: "PRIVATE",
      }),
    );
    const result = await feature.saveUnit.execute(grants, id(99) as OperationId, change, signal());
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "stale_version", correlationId: id(9) },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() =>
      feature.saveUnit.execute(grants, id(99) as OperationId, change, cancelled.signal),
    ).toThrow();
    expect(request).toHaveBeenCalledTimes(5);
  });
  it("checks permission, identity, bounded literal filters and compatible cursors before I/O", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items: [], nextCursor: null });
    const feature = createOrganizationFeature({ request });
    for (const permissions of [[], ["company.manage"], ["people.read"]]) {
      expect(
        await feature.loadUnits.execute({ ...access, permissions }, search, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.loadUnit.execute({ ...access, permissions }, id(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const input of [
      { ...search, query: "a".repeat(121) },
      { ...search, kind: "branch" },
      { ...search, kind: "" },
      { ...search, active: "unknown" },
      { ...search, active: "1" },
      { ...search, after: "" },
      { ...search, after: "BRANCH:../elsewhere" },
      { ...search, after: "BRANCH:A" },
      { ...search, after: `BRANCH:${"A".repeat(33)}` },
      { ...search, kind: "POSITION", after: "DEPARTMENT:SALES" },
    ])
      expect(await feature.loadUnits.execute(access, input, signal())).toMatchObject({ ok: false });
    expect(await feature.loadUnit.execute(access, "../elsewhere", signal())).toMatchObject({
      ok: false,
      failure: { code: "organization_unit_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
    for (const kind of ["BRANCH", "DEPARTMENT", "POSITION", "COST_CENTER"])
      expect(
        await feature.loadUnits.execute(
          access,
          { ...search, kind, after: `${kind}:UNIT-001` },
          signal(),
        ),
      ).toMatchObject({ ok: true });
    expect(request).toHaveBeenCalledTimes(4);
  });

  it("encodes a bounded request and maps only immutable organization fields", async () => {
    const payload = details();
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({
        items: [{ ...unit(), active: false, privateField: "PRIVATE" }],
        nextCursor: null,
      })
      .mockResolvedValueOnce({
        ...payload,
        companyId: companyId.toUpperCase(),
        unit: { ...payload.unit, id: id().toUpperCase(), parentId: id(2).toUpperCase() },
        privateField: "PRIVATE",
      });
    const feature = createOrganizationFeature({ request });
    const page = await feature.loadUnits.execute(
      access,
      {
        ...search,
        query: "  R&D_100% +  ",
        kind: "DEPARTMENT",
        active: "false",
        after: "DEPARTMENT:OLD",
      },
      signal(),
    );
    expect(page.ok).toBe(true);
    const query = new URL(request.mock.calls[0]?.[0].path ?? "", "https://fixture.invalid")
      .searchParams;
    expect(Object.fromEntries(query)).toEqual({
      query: "R&D_100% +",
      limit: "50",
      kind: "DEPARTMENT",
      active: "false",
      after: "DEPARTMENT:OLD",
    });
    const result = await feature.loadUnit.execute(access, id().toUpperCase(), signal());
    if (!page.ok || !result.ok) throw new Error("Organization fixture rejected");
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/organization-units/${id()}`,
    );
    expect(result.value.unit.parentId).toBe(result.value.parent?.id);
    expect(result.value.unit.companyId).toBe(companyId);
    expect(result.value.parent?.companyId).toBe(companyId);
    for (const value of [
      page.value,
      page.value.items,
      page.value.items[0],
      result.value,
      result.value.unit,
      result.value.parent,
    ])
      expect(Object.isFrozen(value)).toBe(true);
    expect(JSON.stringify([page, result])).not.toContain("PRIVATE");
  });

  it("rejects inconsistent company, unit, parent and malformed values inside the failure boundary", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createOrganizationFeature({ request });
    const valid = details();
    for (const payload of [
      { ...valid, companyId: id(8) },
      { ...valid, unit: unit(8) },
      { ...valid, parent: null },
      { ...valid, parent: unit(8, "BRANCH") },
      { ...valid, unit: { ...valid.unit, parentId: null } },
      { ...valid, unit: { ...valid.unit, name: " " } },
      { ...valid, unit: { ...valid.unit, code: "bad code" } },
      { ...valid, unit: { ...valid.unit, timezone: "Asia/Jakarta" } },
      { ...valid, unit: { ...valid.unit, parentId: id() } },
      { ...valid, unit: { ...valid.unit, version: Number.MAX_SAFE_INTEGER + 1 } },
      { ...valid, parent: { ...valid.parent, timezone: null } },
    ]) {
      request.mockResolvedValueOnce(payload);
      expect(await feature.loadUnit.execute(access, id(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    // Archiving a parent does not erase its child; detail reads preserve the actual records.
    request.mockResolvedValueOnce({ ...valid, parent: { ...valid.parent, active: false } });
    expect(await feature.loadUnit.execute(access, id(), signal())).toMatchObject({
      ok: true,
      value: { parent: { active: false } },
    });
    request.mockResolvedValueOnce({ companyId, unit: unit(), parent: null });
    expect(await feature.loadUnit.execute(access, id(), signal())).toMatchObject({
      ok: true,
      value: { parent: null },
    });
  });

  it("bounds pages and rejects duplicate, stalled or mismatched continuations without guessing database collation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createOrganizationFeature({ request });
    const items = Array.from({ length: 50 }, (_, index) => unit(index + 1));
    request.mockResolvedValueOnce({ items, nextCursor: "DEPARTMENT:UNIT-050" });
    expect(await feature.loadUnits.execute(access, search, signal())).toMatchObject({
      ok: true,
      value: { nextCursor: "DEPARTMENT:UNIT-050" },
    });
    for (const page of [
      { items: [unit(), unit()], nextCursor: null },
      { items: [unit(), { ...unit(2), code: unit().code }], nextCursor: null },
      { items: [unit()], nextCursor: "DEPARTMENT:UNIT-001" },
      { items, nextCursor: "DEPARTMENT:UNIT-049" },
      { items: [...items, unit(51)], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(await feature.loadUnits.execute(access, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    for (const input of [
      { ...search, after: "DEPARTMENT:UNIT-001" },
      { ...search, kind: "BRANCH" },
      { ...search, active: "false" },
    ]) {
      request.mockResolvedValueOnce({ items: [unit()], nextCursor: null });
      expect(await feature.loadUnits.execute(access, input, signal())).toMatchObject({ ok: false });
    }
    request.mockResolvedValueOnce({
      items: [
        { ...unit(1, "BRANCH"), code: "UNIT_A" },
        { ...unit(2, "BRANCH"), code: "UNIT-B" },
        { ...unit(3), code: "UNIT_A" },
      ],
      nextCursor: null,
    });
    expect(await feature.loadUnits.execute(access, search, signal())).toMatchObject({ ok: true });
  });

  it("preserves localizable failures and cancellation without retries or raw error details", async () => {
    const correlationId = id(9);
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(403, { code: "mfa_required", correlationId, detail: "PRIVATE" }),
      )
      .mockRejectedValueOnce(new TypeError("PRIVATE NETWORK ERROR"));
    const feature = createOrganizationFeature({ request });
    const failure = await feature.loadUnit.execute(access, id(), signal());
    expect(failure).toMatchObject({ ok: false, failure: { code: "mfa_required", correlationId } });
    expect(JSON.stringify(failure)).not.toContain("PRIVATE");
    expect(await feature.loadUnits.execute(access, search, signal())).toMatchObject({
      ok: false,
      failure: { code: "connection_unavailable" },
    });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadUnit.execute(access, id(), cancelled.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
    let finish!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const owner = new AbortController();
    const late = feature.loadUnit.execute(access, id(), owner.signal);
    owner.abort();
    finish(details());
    await expect(late).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(3);
  });
});
