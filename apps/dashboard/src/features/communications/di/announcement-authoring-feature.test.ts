import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { OperationId } from "../../../core/domain/identifiers";
import type { AnnouncementChange } from "../domain/entities/announcement-change";
import type { AudienceReferenceSearch } from "../domain/entities/audience-reference-search";
import { createCommunicationsFeature } from "./communications-feature";
import { access, announcementId, companyId } from "./communications-fixture";

const operation = "11000000-0000-4000-8000-000000000001" as OperationId;
const target = "51000000-0000-4000-8000-000000000001";
const input: AnnouncementChange = {
  id: announcementId,
  expectedVersion: null,
  title: " Office hours ",
  body: " New hours.\r\nPlease review. ",
  audienceKind: "BRANCH",
  targetIds: [target],
  acknowledgementRequired: true,
  reason: " Schedule update ",
};
const search: AudienceReferenceSearch = { kind: "BRANCH", query: "", ids: [], after: null };
const reference = {
  id: target,
  kind: "BRANCH",
  name: "South office",
  code: "SOUTH",
  version: 3,
  active: true,
};
const signal = () => new AbortController().signal;

describe("announcement commands and reference discovery", () => {
  it("maps a normalized immutable command to a scoped operation and validates its receipt", async () => {
    const request = vi.fn(async (_input: HttpRequest) => ({
      id: announcementId.toUpperCase(),
      version: 0,
      replayed: true,
    }));
    const result = await createCommunicationsFeature({ request }).saveAnnouncement.execute(
      access,
      operation,
      input,
      signal(),
    );
    expect(result).toEqual({ ok: true, value: { id: announcementId, version: 0 } });
    expect(request.mock.calls[0]?.[0]).toMatchObject({
      path: `/api/v1/companies/${companyId}/announcements/${announcementId}`,
      method: "PUT",
      operationId: operation,
      body: {
        title: "Office hours",
        body: "New hours.\nPlease review.",
        audience: { kind: "BRANCH", targetIds: [target] },
        acknowledgementRequired: true,
        reason: "Schedule update",
        expectedVersion: null,
      },
    });
    if (!result.ok) throw new Error("Expected receipt");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(input.body).toContain("\r\n");
  });
  it.each([
    { title: " " },
    { title: "x".repeat(201) },
    { body: "\0" },
    { body: "x".repeat(16001) },
    { targetIds: [] },
    { targetIds: [target, target.toUpperCase()] },
    { targetIds: ["../../account"] },
    { audienceKind: "COMPANY" as const },
    { expectedVersion: -1 },
    { expectedVersion: Number.MAX_SAFE_INTEGER },
    { reason: "" },
  ])("validates the draft before acquiring data", async (change) => {
    const request = vi.fn();
    expect(
      await createCommunicationsFeature({ request }).saveAnnouncement.execute(
        access,
        operation,
        { ...input, ...change },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_announcement" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("retains technical failure classification and rejects a foreign or wrong-version receipt", async () => {
    let response: unknown = { id: target, version: 0 };
    const feature = createCommunicationsFeature({ request: async () => response });
    expect(
      await feature.saveAnnouncement.execute(access, operation, input, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    response = { id: announcementId, version: 2 };
    expect(
      await feature.saveAnnouncement.execute(access, operation, input, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    const rejected = createCommunicationsFeature({
      request: async () => {
        throw new HttpResponseError(409, { code: "stale_version" });
      },
    });
    expect(
      await rejected.saveAnnouncement.execute(access, operation, input, signal()),
    ).toMatchObject({ ok: false, failure: { code: "stale_version" } });
  });
  it("prevents feature reads and writes without management permission", async () => {
    const request = vi.fn();
    const feature = createCommunicationsFeature({ request });
    const denied = { ...access, permissions: ["announcements.read"] };
    expect(
      await feature.saveAnnouncement.execute(denied, operation, input, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await feature.loadReferences.execute(denied, search, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(request).not.toHaveBeenCalled();
  });
  it("requests one bounded literal search and permits inactive labels only for selected IDs", async () => {
    let response = { items: [reference], nextCursor: null };
    const request = vi.fn(async (_input: HttpRequest) => response);
    const feature = createCommunicationsFeature({ request });
    const result = await feature.loadReferences.execute(
      access,
      { ...search, query: " South %_ " },
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { items: [reference] } });
    const url = new URL(request.mock.calls[0]?.[0].path ?? "", "https://example.invalid");
    expect(url.searchParams.get("query")).toBe("South %_");
    expect(url.searchParams.get("limit")).toBe("50");
    response = { items: [{ ...reference, active: false }], nextCursor: null };
    expect(await feature.loadReferences.execute(access, search, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    expect(
      await feature.loadReferences.execute(access, { ...search, ids: [target] }, signal()),
    ).toMatchObject({ ok: true });
    const lookup = new URL(request.mock.calls.at(-1)?.[0].path ?? "", "https://example.invalid");
    expect(lookup.searchParams.getAll("ids")).toEqual([target]);
    expect(lookup.searchParams.has("query")).toBe(false);
  });
  it.each([
    { items: [reference, reference], nextCursor: null },
    { items: [{ ...reference, kind: "DEPARTMENT" }], nextCursor: null },
    { items: [reference], nextCursor: target },
    { items: [{ ...reference, active: null }], nextCursor: null },
    { items: [{ ...reference, code: null }], nextCursor: null },
  ])("rejects incompatible or nonprogressing reference pages", async (response) => {
    expect(
      await createCommunicationsFeature({ request: async () => response }).loadReferences.execute(
        access,
        search,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("validates lookup bounds and preserves cancellation after a pending read", async () => {
    const request = vi.fn();
    const feature = createCommunicationsFeature({ request });
    for (const change of [
      { query: "x".repeat(121) },
      { ids: [target, target] },
      { ids: [target], query: "Office" },
      { ids: [target], after: target },
      { after: "../next" },
    ])
      expect(
        await feature.loadReferences.execute(access, { ...search, ...change }, signal()),
      ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
    let complete!: (value: unknown) => void;
    const late = createCommunicationsFeature({
      request: () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    });
    const owner = new AbortController();
    const read = late.loadReferences.execute(access, search, owner.signal);
    owner.abort();
    complete({ items: [reference], nextCursor: null });
    await expect(read).rejects.toHaveProperty("name", "AbortError");
  });
});
