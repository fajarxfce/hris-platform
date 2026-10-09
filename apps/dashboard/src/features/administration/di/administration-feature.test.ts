import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { defaultAuditSearch } from "../domain/entities/audit-search";
import { createAdministrationFeature } from "./administration-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const otherCompany = "10000000-0000-4000-8000-000000000002";
const access = { companyId, permissions: ["audit.read"] };
const id = (value: number) => `40000000-0000-4000-8000-${String(value).padStart(12, "0")}`;
const event = (value: number) => ({
  id: id(value),
  companyId,
  actorId: "20000000-abcd-4abc-8000-000000000001",
  resourceType: "employment",
  resourceId: "30000000-0000-4000-8000-000000000001",
  action: "employment.created",
  correlationId: "50000000-0000-4000-8000-000000000001",
  recordedAt: "2026-09-25T00:00:00Z",
});
const dto = {
  companyId,
  from: "2026-09-01T00:00:00Z",
  until: "2026-10-01T00:00:00Z",
  evaluatedAt: "2026-10-01T00:00:00.000000123Z",
  items: [event(2), event(1)],
  nextCursor: null,
};
const signal = () => new AbortController().signal;

describe("audit feature boundaries", () => {
  it("checks permission, bounded UTC windows, filter syntax and continuation inputs before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createAdministrationFeature({ request });
    expect(
      await feature.searchAudit.execute(
        { companyId, permissions: ["company.read"] },
        defaultAuditSearch,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const query of [
      { from: "2026-02-30T00:00:00Z" },
      { until: "2026-10-01T00:00:00.0000000001Z" },
      { until: "2026-10-01T07:00:00+07:00" },
      { from: "1899-12-31T00:00:00Z" },
      { from: dto.until, until: dto.from },
      { from: dto.from, until: dto.from },
      { from: "2026-01-01T00:00:00Z", until: "2026-04-02T00:00:00Z" },
    ])
      expect(
        await feature.searchAudit.execute(access, { ...defaultAuditSearch, ...query }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_audit_range" } });
    for (const query of [
      { actorId: "invalid" },
      { resourceId: "" },
      { action: "x".repeat(101) },
      { resourceType: "x".repeat(81) },
      { action: "x' OR 1=1" },
    ]) {
      expect(
        await feature.searchAudit.execute(access, { ...defaultAuditSearch, ...query }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_audit_filter" } });
    }
    for (const query of [
      { cursor: id(1) },
      { cursor: "invalid", from: dto.from, until: dto.until },
    ])
      expect(
        await feature.searchAudit.execute(access, { ...defaultAuditSearch, ...query }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_audit_cursor" } });
    expect(request).not.toHaveBeenCalled();
  });

  it("preserves microsecond order and scopes a bounded immutable metadata page", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...dto,
      items: [
        { ...event(1), recordedAt: "2026-09-25T00:00:00.000002Z", reason: "PRIVATE CHANGE" },
        {
          ...event(2),
          recordedAt: "2026-09-25T00:00:00.000001Z",
          details: { value: "PRIVATE PAYLOAD" },
        },
      ],
    });
    const feature = createAdministrationFeature({ request });
    const result = await feature.searchAudit.execute(access, defaultAuditSearch, signal());
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error("Audit fixture was rejected");
    expect(result.value.items.map((item) => item.id)).toEqual([id(1), id(2)]);
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.items)).toBe(true);
    expect(Object.isFrozen(result.value.items[0])).toBe(true);
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/audit-events?limit=50`,
    );
    for (const changes of [
      { companyId: otherCompany },
      { items: [{ ...event(1), companyId: otherCompany }] },
      { from: "2026-08-31T00:00:00Z" },
      { evaluatedAt: "2026-09-01T00:00:00Z" },
      { items: [event(1), event(1)] },
      { items: [event(1), event(2)] },
      { items: [{ ...event(1), recordedAt: dto.until }] },
      { items: [{ ...event(1), recordedAt: "2026-08-31T23:59:59.999999Z" }] },
      { nextCursor: id(1) },
      { items: Array.from({ length: 51 }, (_, index) => event(100 - index)) },
      {
        items: [
          { ...event(2), recordedAt: "2026-09-25T00:00:00.000001Z" },
          { ...event(1), recordedAt: "2026-09-25T00:00:00.000002Z" },
        ],
      },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await feature.searchAudit.execute(access, defaultAuditSearch, signal())).toMatchObject(
        { ok: false, failure: { code: "invalid_response" } },
      );
    }
  });

  it("keeps exact filters and server-resolved windows during pagination and rejects non-progressing pages", async () => {
    const first = {
      ...dto,
      items: Array.from({ length: 50 }, (_, index) => event(100 - index)),
      nextCursor: id(51),
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(first)
      .mockResolvedValue(dto);
    const feature = createAdministrationFeature({ request });
    const query = {
      ...defaultAuditSearch,
      action: "employment.created",
      actorId: event(1).actorId.toUpperCase(),
      resourceType: "employment",
      resourceId: event(1).resourceId,
    };
    const loaded = await feature.searchAudit.execute(access, query, signal());
    expect(loaded).toMatchObject({ ok: true, value: { nextCursor: id(51) } });
    const next = { ...query, from: dto.from, until: dto.until, cursor: id(51) };
    expect(await feature.searchAudit.execute(access, next, signal())).toMatchObject({ ok: true });
    const parameters = new URL(request.mock.lastCall?.[0].path ?? "", "https://fixture.test")
      .searchParams;
    expect(Object.fromEntries(parameters)).toEqual({
      ...next,
      actorId: event(1).actorId,
      limit: "50",
    });
    for (const changes of [
      { items: [event(51)] },
      { items: [{ ...event(1), action: "employment.changed" }] },
      { items: [{ ...event(1), actorId: id(3) }] },
      { items: [{ ...event(1), resourceId: id(4) }] },
      { items: [{ ...event(1), resourceType: "company" }] },
      { from: "2026-09-02T00:00:00Z" },
      { until: "2026-09-30T23:59:59Z" },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await feature.searchAudit.execute(access, next, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ ...dto, items: [] });
    expect(await feature.searchAudit.execute(access, next, signal())).toMatchObject({
      ok: true,
      value: { items: [], nextCursor: null },
    });
  });

  it("retains localizable failures without raw detail, retries or swallowed cancellation", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(422, {
          code: "invalid_audit_range",
          parameters: { maximumDays: "90" },
          detail: "PRIVATE FAILURE",
        }),
      )
      .mockResolvedValue(dto);
    const feature = createAdministrationFeature({ request });
    const result = await feature.searchAudit.execute(access, defaultAuditSearch, signal());
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "invalid_audit_range", parameters: { maximumDays: "90" } },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledTimes(1);
    const abort = new AbortController();
    const pending = feature.searchAudit.execute(access, defaultAuditSearch, abort.signal);
    abort.abort();
    await expect(pending).rejects.toThrow();
    expect(() => feature.searchAudit.execute(access, defaultAuditSearch, abort.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(2);
  });
});
