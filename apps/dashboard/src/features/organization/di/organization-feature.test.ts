import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { OrganizationUnitDto } from "../data/models/organization-unit-dto";
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
