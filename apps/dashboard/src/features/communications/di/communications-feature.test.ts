import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import { createCommunicationsFeature } from "./communications-feature";
import { access, announcementId, companyId, detail, summary } from "./communications-fixture";

describe("announcement reads", () => {
  it("checks management access and route inputs before I/O", async () => {
    const request = vi.fn();
    const feature = createCommunicationsFeature({ request });
    const denied = { ...access, permissions: ["announcements.read"] };
    const signal = new AbortController().signal;
    expect(await feature.loadAnnouncements.execute(denied, null, signal)).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(
      await feature.loadAnnouncement.execute(denied, announcementId, null, signal),
    ).toMatchObject({ ok: false });
    expect(await feature.loadAnnouncements.execute(access, "foreign/route", signal)).toMatchObject({
      ok: false,
    });
    expect(await feature.loadAnnouncement.execute(access, "../secret", null, signal)).toMatchObject(
      { ok: false },
    );
    expect(
      await feature.loadHistory.execute(access, announcementId, "9007199254740992", signal),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });
  it("requests a bounded company page and maps an immutable revision without DTO fields", async () => {
    const raw = detail(3);
    raw.audience = { kind: "GROUP", targetIds: ["41000000-0000-4000-8000-000000000001"] };
    const request = vi.fn(async (input: HttpRequest) =>
      input.path.includes("/revisions/") ? raw : { items: [summary()], nextCursor: null },
    );
    const feature = createCommunicationsFeature({ request });
    const signal = new AbortController().signal;
    expect(await feature.loadAnnouncements.execute(access, null, signal)).toMatchObject({
      ok: true,
      value: { items: [{ companyId }], nextCursor: null },
    });
    expect(request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${companyId}/announcements?limit=50`,
    );
    const result = await feature.loadAnnouncement.execute(access, announcementId, "3", signal);
    expect(request.mock.calls[1]?.[0].path).toContain("/revisions/3");
    if (!result.ok) throw new Error("Expected revision");
    expect(result.value).not.toHaveProperty("audience");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.targetIds)).toBe(true);
    raw.audience.targetIds.length = 0;
    expect(result.value.targetIds).toHaveLength(1);
  });
  it.each([
    { items: [summary(), summary()], nextCursor: null },
    { items: [summary()], nextCursor: announcementId },
    { items: [{ ...summary(), audienceKind: "GROUP", targetCount: 0 }], nextCursor: null },
    { items: [{ ...summary(), status: "UNKNOWN" }], nextCursor: null },
    { items: [{ ...summary(), recordedAt: "invalid" }], nextCursor: null },
  ])("rejects malformed, duplicate or nonprogressing pages", async (raw) => {
    const feature = createCommunicationsFeature({ request: async () => raw });
    expect(
      await feature.loadAnnouncements.execute(access, null, new AbortController().signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("keeps ordered history versions and rejects a resource/version mismatch", async () => {
    let response: unknown = { items: [summary(1), summary(2)], nextCursor: null };
    const feature = createCommunicationsFeature({ request: async () => response });
    const signal = new AbortController().signal;
    expect(await feature.loadHistory.execute(access, announcementId, "0", signal)).toMatchObject({
      ok: true,
    });
    response = { items: [summary(2), summary(1)], nextCursor: null };
    expect(await feature.loadHistory.execute(access, announcementId, null, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    response = detail(2);
    expect(
      await feature.loadAnnouncement.execute(access, announcementId, "1", signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    response = { ...detail(), id: "21000000-0000-4000-8000-000000000002" };
    expect(
      await feature.loadAnnouncement.execute(access, announcementId, null, signal),
    ).toMatchObject({ ok: false });
  });
  it("preserves safe API codes and propagates cancellation after pending I/O", async () => {
    const feature = createCommunicationsFeature({
      request: async () => {
        throw new HttpResponseError(403, {
          code: "company_access_denied",
          detail: "PRIVATE DETAIL",
        });
      },
    });
    const result = await feature.loadAnnouncements.execute(
      access,
      null,
      new AbortController().signal,
    );
    expect(result).toEqual({
      ok: false,
      failure: { code: "company_access_denied", fields: {}, parameters: {} },
    });
    const owner = new AbortController();
    const canceled = createCommunicationsFeature({
      request: async () => {
        owner.abort();
        return { items: [summary()], nextCursor: null };
      },
    });
    await expect(
      canceled.loadAnnouncements.execute(access, null, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
