import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { OperationId } from "../../../core/domain/identifiers";
import { toAnnouncement } from "../data/mappers/announcement-mapper";
import type { AnnouncementId } from "../domain/entities/announcement";
import { previewDetail, publicationJobId, reviewDetail } from "./announcement-publication-fixture";
import { createCommunicationsFeature } from "./communications-feature";
import { access, announcementId, companyId, detail } from "./communications-fixture";

const signal = () => new AbortController().signal;
const operation = "91000000-0000-4000-8000-000000000001" as OperationId;
const command = { id: announcementId, expectedVersion: 0, reason: " Publication review " };
const announcement = () =>
  toAnnouncement(detail(), companyId, announcementId as AnnouncementId, null);

describe("publication API contracts", () => {
  it("maps a bounded immutable review and an independent versioned audience preview", async () => {
    let response: unknown = reviewDetail();
    const request = vi.fn(async (_request: HttpRequest) => response);
    const feature = createCommunicationsFeature({ request });
    const review = await feature.loadReview.execute(access, announcementId, signal());
    expect(review).toMatchObject({
      ok: true,
      value: { availableActions: ["EDIT", "PREVIEW", "PUBLISH", "ARCHIVE"] },
    });
    expect(request.mock.calls[0]?.[0].path).toContain("/publication-review");
    if (!review.ok) throw new Error("Missing review");
    expect(Object.isFrozen(review.value.availableActions)).toBe(true);
    response = previewDetail();
    expect(
      await feature.previewAudience.execute(access, review.value.announcement, signal()),
    ).toMatchObject({ ok: true, value: { recipientCount: 3 } });
    expect(request.mock.calls[1]?.[0].path).toContain("/audience-preview?expectedVersion=0");
  });
  it.each([
    { ...reviewDetail(), announcement: { ...detail(), id: publicationJobId } },
    { ...reviewDetail(), availableActions: ["ARCHIVE", "ARCHIVE"] },
    { ...reviewDetail(), availableActions: ["VIEW_JOB"] },
    {
      ...reviewDetail(),
      publicationJob: {
        id: publicationJobId,
        status: "QUEUED",
        version: 0,
        cancellationRequested: false,
        failureCode: null,
      },
    },
    { ...reviewDetail(), announcement: { ...detail(), version: 1000 } },
  ])("rejects incompatible identities, duplicate actions and unmatched jobs", async (response) => {
    expect(
      await createCommunicationsFeature({ request: async () => response }).loadReview.execute(
        access,
        announcementId,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves minimal linked-job metadata and a matching named-audience preview", async () => {
    const group = "61000000-0000-4000-8000-000000000001";
    const dto = {
      ...detail(1),
      status: "QUEUED" as const,
      publicationJobId,
      audience: { kind: "GROUP" as const, targetIds: [group] },
    };
    const target = toAnnouncement(dto, companyId, announcementId as AnnouncementId, null);
    const response = { ...previewDetail(1), audienceVersions: { [group.toUpperCase()]: 4 } };
    const result = await createCommunicationsFeature({
      request: async () => response,
    }).previewAudience.execute(access, target, signal());
    expect(result).toMatchObject({ ok: true, value: { references: [{ id: group, version: 4 }] } });
    if (result.ok) expect(Object.isFrozen(result.value.references)).toBe(true);
  });
  it.each([
    { ...previewDetail(), announcementId: publicationJobId },
    { ...previewDetail(), version: 1 },
    { ...previewDetail(), recipientCount: 5001 },
    { ...previewDetail(), audienceVersions: { [publicationJobId]: 0 } },
  ])("rejects previews that do not match the observed content", async (response) => {
    expect(
      await createCommunicationsFeature({ request: async () => response }).previewAudience.execute(
        access,
        announcement(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("does not preview published, foreign-company or unauthorized content", async () => {
    const request = vi.fn();
    const feature = createCommunicationsFeature({ request });
    const denied = { ...access, permissions: [] };
    expect(await feature.loadReview.execute(denied, announcementId, signal())).toMatchObject({
      ok: false,
    });
    expect(await feature.previewAudience.execute(denied, announcement(), signal())).toMatchObject({
      ok: false,
    });
    expect(
      await feature.previewAudience.execute(
        access,
        { ...announcement(), status: "PUBLISHED" },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "announcement_preview_unavailable" } });
    expect(
      await feature.previewAudience.execute(
        { ...access, companyId: publicationJobId as typeof companyId },
        announcement(),
        signal(),
      ),
    ).toMatchObject({ ok: false });
    for (const action of [feature.publish, feature.archive, feature.returnToDraft]) {
      expect(
        await action.execute(denied, operation, { ...command, scheduledFor: null }, signal()),
      ).toMatchObject({ ok: false });
    }
    expect(request).not.toHaveBeenCalled();
  });
  it("translates each command to its own endpoint with an observed version", async () => {
    const request = vi.fn(async (_request: HttpRequest) => ({ id: announcementId, version: 1 }));
    const feature = createCommunicationsFeature({ request });
    expect(
      await feature.publish.execute(
        access,
        operation,
        { ...command, scheduledFor: "2026-11-01T01:00:00Z" },
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(await feature.archive.execute(access, operation, command, signal())).toMatchObject({
      ok: true,
    });
    expect(await feature.returnToDraft.execute(access, operation, command, signal())).toMatchObject(
      { ok: true },
    );
    expect(request.mock.calls.map(([input]) => input.path.split("/").at(-1))).toEqual([
      "publish",
      "archive",
      "return-to-draft",
    ]);
    expect(request.mock.calls[0]?.[0]).toMatchObject({
      method: "POST",
      operationId: operation,
      body: {
        expectedVersion: 0,
        reason: "Publication review",
        scheduledFor: "2026-11-01T01:00:00Z",
      },
    });
    expect(request.mock.calls[1]?.[0].body).toEqual({
      expectedVersion: 0,
      reason: "Publication review",
    });
  });
  it("does not use the browser clock to block recovery of an old scheduled command", async () => {
    const request = vi.fn(async () => ({ id: announcementId, version: 1, replayed: true }));
    const result = await createCommunicationsFeature({ request }).publish.execute(
      access,
      operation,
      { ...command, scheduledFor: "2020-01-01T01:00:00Z" },
      signal(),
    );
    expect(result).toMatchObject({ ok: true });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it.each([
    { expectedVersion: -1 },
    { expectedVersion: 999 },
    { reason: "" },
    { reason: "x".repeat(1001) },
    { scheduledFor: "2026-02-30T00:00:00Z" },
    { scheduledFor: "2026-11-01T01:00:00.1234567Z" },
    { scheduledFor: "2026-11-01T01:00:00+07:00" },
  ])("rejects malformed commands without I/O", async (input) => {
    const request = vi.fn();
    expect(
      await createCommunicationsFeature({ request }).publish.execute(
        access,
        operation,
        { ...command, scheduledFor: null, ...input },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_announcement_command" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("preserves specific conflicts and rejects unrelated or wrong-version receipts", async () => {
    const feature = createCommunicationsFeature({
      request: async () => {
        throw new HttpResponseError(409, { code: "announcement_publication_active" });
      },
    });
    expect(await feature.archive.execute(access, operation, command, signal())).toMatchObject({
      ok: false,
      failure: { code: "announcement_publication_active" },
    });
    for (const response of [
      { id: publicationJobId, version: 1 },
      { id: announcementId, version: 2 },
    ]) {
      expect(
        await createCommunicationsFeature({ request: async () => response }).archive.execute(
          access,
          operation,
          command,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
});
