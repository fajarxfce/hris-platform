import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId } from "../../../core/domain/identifiers";
import { createAdministrationFeature } from "./administration-feature";

const access = {
  accountId: "10000000-0000-4000-8000-000000000001" as AccountId,
  permissions: ["audit.read", "identity.manage"],
};
const event = {
  id: "40000000-0000-4000-8000-000000000001",
  actorId: access.accountId,
  resourceId: "30000000-0000-4000-8000-000000000001",
  resourceType: "account",
  action: "identity.account_access_saved",
  correlationId: "50000000-0000-4000-8000-000000000001",
  recordedAt: "2026-10-01T00:00:00Z",
};
const detail = {
  id: event.id,
  accountId: event.resourceId,
  active: true,
  permissions: ["audit.read"],
  reason: "Saved reason",
};
const signal = () => new AbortController().signal;

describe("typed account audit boundary", () => {
  it("requires both global grants, supported source and canonical IDs before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const review = createAdministrationFeature({ request }).getPlatformAccountAudit;
    for (const permissions of [[], ["audit.read"], ["identity.manage"]])
      expect(await review.execute({ ...access, permissions }, event, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    for (const change of [
      { resourceType: "company" },
      { action: "identity.other" },
      { id: "../account" },
      { resourceId: "bad" },
    ])
      expect(await review.execute(access, { ...event, ...change }, signal())).toMatchObject({
        ok: false,
        failure: { code: "audit_detail_not_found" },
      });
    expect(request).not.toHaveBeenCalled();
  });

  it("accepts only bounded matching historical fields and returns detached immutable permissions", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(detail);
    const review = createAdministrationFeature({ request }).getPlatformAccountAudit;
    const result = await review.execute(access, event, signal());
    expect(result).toEqual({ ok: true, value: detail });
    if (!result.ok) throw new Error("Expected saved access");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.permissions)).toBe(true);
    expect(result.value.permissions).not.toBe(detail.permissions);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/administration/audit-events/${event.id}/account-access`,
    );
    for (const changes of [
      { id: event.correlationId },
      { accountId: access.accountId },
      { actorId: access.accountId },
      { companyId: access.accountId },
      { details: { private: "PRIVATE" } },
      { active: "true" },
      { permissions: ["audit.read", "audit.read"] },
      { permissions: ["PRIVATE INVALID"] },
      { permissions: Array.from({ length: 33 }, (_, i) => `fixture.${i}`) },
      { reason: "x".repeat(1001) },
      { reason: "😀".repeat(1001) },
    ]) {
      request.mockResolvedValueOnce({ ...detail, ...changes });
      expect(await review.execute(access, event, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ ...detail, active: false, permissions: [], reason: null });
    expect(await review.execute(access, event, signal())).toMatchObject({
      ok: true,
      value: { active: false, permissions: [], reason: null },
    });
    request.mockResolvedValueOnce({ ...detail, reason: "😀".repeat(1000) });
    expect((await review.execute(access, event, signal())).ok).toBe(true);
  });

  it("preserves safe failures and cancellation without publishing private exception details", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(
        new HttpResponseError(404, { code: "audit_detail_not_found", detail: "PRIVATE" }),
      );
    const review = createAdministrationFeature({ request }).getPlatformAccountAudit;
    expect(await review.execute(access, event, signal())).toMatchObject({
      ok: false,
      failure: { code: "audit_detail_not_found" },
    });
    request.mockRejectedValueOnce(new Error("PRIVATE"));
    const failure = await review.execute(access, event, signal());
    expect(failure.ok).toBe(false);
    expect(JSON.stringify(failure)).not.toContain("PRIVATE");
    const abort = new AbortController();
    request.mockImplementationOnce(async () => {
      abort.abort();
      return detail;
    });
    await expect(review.execute(access, event, abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(() => review.execute(access, event, abort.signal)).toThrow();
  });
});
