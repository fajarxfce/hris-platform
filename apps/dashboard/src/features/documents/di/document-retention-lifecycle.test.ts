import { describe, expect, it, vi } from "vitest";
import { documentFileInput } from "../../../../tests/fixtures/document-file-input";
import {
  archivedDocumentRetention,
  documentRetention,
  documentRetentionChange,
} from "../../../../tests/fixtures/document-retention";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
} from "../../../../tests/fixtures/documents";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toDocumentRetention } from "../data/mappers/document-retention-state-mapper";
import type { DocumentRetentionDto } from "../data/models/document-retention-state-dto";
import type { DocumentRetentionIntent } from "../domain/entities/document-retention";
import { createDocumentsFeature } from "./documents-feature";

const access = { companyId: documentCompanyId as CompanyId, permissions: ["documents.retention"] },
  operation = documentId(800) as OperationId;
const signal = () => new AbortController().signal;
function feature() {
  const request = vi.fn<HttpClient["request"]>();
  return {
    request,
    retention: createDocumentsFeature({ request }, { downloadBinary: vi.fn() }, documentFileInput())
      .retention,
  };
}
const state = (raw = documentRetention()) =>
  toDocumentRetention(raw, access.companyId, documentHeaderId);

describe("document retention lifecycle boundaries", () => {
  it("requires the independent grant before reads, review and changes", async () => {
    const f = feature(),
      limited = {
        ...access,
        permissions: [
          "documents.read",
          "documents.manage",
          "people.profile.read",
          "people.profile.manage",
        ],
      };
    for (const result of await Promise.all([
      f.retention.load.execute(limited, documentHeaderId, null, signal()),
      f.retention.reviewChange.execute(limited, documentHeaderId, "archive", signal()),
      f.retention.change.execute(limited, operation, state(), "archive", "Review", signal()),
    ]))
      expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("loads separate immutable lifecycle metadata without reading employee or file details", async () => {
    const f = feature(),
      current = archivedDocumentRetention(0);
    f.request
      .mockResolvedValueOnce(current)
      .mockResolvedValueOnce({ items: [documentRetentionChange()], nextCursor: null });
    const result = await f.retention.load.execute(access, documentHeaderId, null, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        current: { companyId: documentCompanyId, version: 0 },
        history: { nextCursor: null },
      },
    });
    if (!result.ok) throw new Error("Expected lifecycle metadata");
    expect(Object.isFrozen(result.value.current.archive)).toBe(true);
    expect(Object.isFrozen(result.value.history.items)).toBe(true);
    expect(Object.isFrozen(result.value.history.items[0]?.state.archive)).toBe(true);
    if (current.archive) current.archive.retentionDays = 1;
    expect(result.value.current.archive?.retentionDays).toBe(365);
    expect(f.request.mock.calls.map(([request]) => request.path)).toEqual([
      `/api/v1/companies/${documentCompanyId}/documents/${documentHeaderId}/retention`,
      `/api/v1/companies/${documentCompanyId}/documents/${documentHeaderId}/retention/history?limit=20`,
    ]);
  });
  it("preserves a never-changed lifecycle with a nullable observed version", async () => {
    const f = feature();
    f.request
      .mockResolvedValueOnce(documentRetention())
      .mockResolvedValueOnce({ items: [], nextCursor: null });
    expect(await f.retention.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
      ok: true,
      value: { current: { version: null, archive: null, legalHold: false } },
    });
  });
  it.each([
    { ...documentRetention(), documentId: documentId(22) },
    { ...documentRetention(), legalHold: true },
    { ...archivedDocumentRetention(), version: null },
    {
      ...archivedDocumentRetention(),
      archive: { ...archivedDocumentRetention().archive, policyId: null },
    },
    {
      ...archivedDocumentRetention(),
      archive: { ...archivedDocumentRetention().archive, policyVersion: null },
    },
    {
      ...archivedDocumentRetention(),
      archive: { ...archivedDocumentRetention().archive, retentionDays: null },
    },
    {
      ...archivedDocumentRetention(),
      archive: { ...archivedDocumentRetention().archive, eligibleAt: null },
    },
    {
      ...archivedDocumentRetention(),
      archive: { ...archivedDocumentRetention().archive, eligibleAt: "2025-10-02T00:00:00Z" },
    },
  ])("rejects incoherent lifecycle metadata %j", async (raw) => {
    const f = feature();
    f.request.mockResolvedValueOnce(raw);
    expect(await f.retention.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  it.each([true, false])(
    "preserves indefinite archive snapshots with configured=%s",
    async (configured) => {
      const f = feature(),
        raw = archivedDocumentRetention();
      if (!raw.archive) throw new Error("Fixture archive missing");
      raw.archive = {
        ...raw.archive,
        retentionDays: null,
        eligibleAt: null,
        ...(configured ? {} : { policyId: null, policyVersion: null }),
      };
      f.request.mockResolvedValueOnce(raw);
      expect(
        await f.retention.reviewChange.execute(access, documentHeaderId, "restore", signal()),
      ).toMatchObject({ ok: true, value: { archive: { retentionDays: null, eligibleAt: null } } });
    },
  );
  it("rejects history that advances beyond the separately acquired head", async () => {
    const f = feature();
    f.request
      .mockResolvedValueOnce(documentRetention())
      .mockResolvedValueOnce({ items: [documentRetentionChange()], nextCursor: null });
    expect(await f.retention.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "document_retention_state_changed" },
    });
    expect(f.request).toHaveBeenCalledTimes(2);
  });
  it.each([
    { ...documentRetentionChange(), state: documentRetention() },
    { ...documentRetentionChange(), state: documentRetention(0) },
    { ...documentRetentionChange(), action: "RESTORE" },
    { ...documentRetentionChange(), action: "PLACE_HOLD" },
    { ...documentRetentionChange(), action: "RETIRE", retiredRevisionId: null },
    { ...documentRetentionChange(), retiredRevisionId: documentId(31) },
  ])("rejects history contradicting the resulting state %j", async (change) => {
    const f = feature();
    f.request
      .mockResolvedValueOnce(archivedDocumentRetention())
      .mockResolvedValueOnce({ items: [change], nextCursor: null });
    expect(await f.retention.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("paginates ascending lifecycle history without accepting stalled or duplicate cursors", async () => {
    const f = feature();
    f.request.mockResolvedValueOnce(documentRetention(21)).mockResolvedValueOnce({
      items: Array.from({ length: 20 }, (_, version) => documentRetentionChange(version)),
      nextCursor: "19",
    });
    expect(await f.retention.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
      ok: true,
      value: { history: { nextCursor: "19" } },
    });
    f.request.mockResolvedValueOnce(documentRetention(21)).mockResolvedValueOnce({
      items: [documentRetentionChange(20), documentRetentionChange(21)],
      nextCursor: null,
    });
    expect(await f.retention.load.execute(access, documentHeaderId, "19", signal())).toMatchObject({
      ok: true,
      value: { history: { nextCursor: null } },
    });
    f.request
      .mockResolvedValueOnce(documentRetention(21))
      .mockResolvedValueOnce({ items: [documentRetentionChange(19)], nextCursor: "19" });
    expect(await f.retention.load.execute(access, documentHeaderId, "19", signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it.each([
    ["archive", documentRetention()],
    ["restore", archivedDocumentRetention()],
    ["hold", archivedDocumentRetention()],
    ["release-hold", { ...archivedDocumentRetention(1), legalHold: true }],
  ] satisfies [DocumentRetentionIntent, DocumentRetentionDto][])(
    "reviews and submits %s against the separate observed version",
    async (intent, raw) => {
      const f = feature();
      f.request
        .mockResolvedValueOnce(raw)
        .mockResolvedValueOnce({ id: documentHeaderId, version: (raw.version ?? -1) + 1 });
      const review = await f.retention.reviewChange.execute(
        access,
        documentHeaderId,
        intent,
        signal(),
      );
      if (!review.ok) throw new Error("Expected review");
      expect(
        await f.retention.change.execute(
          access,
          operation,
          review.value,
          intent,
          " Reviewed action ",
          signal(),
        ),
      ).toMatchObject({
        ok: true,
        value: { id: documentHeaderId, version: (raw.version ?? -1) + 1 },
      });
      expect(f.request.mock.calls[1]?.[0]).toEqual({
        path: `/api/v1/companies/${documentCompanyId}/documents/${documentHeaderId}/retention/${intent}`,
        method: "POST",
        operationId: operation,
        body: { expectedVersion: raw.version, reason: "Reviewed action" },
      });
    },
  );
  it("rejects ineligible transitions, stale scope and invalid reasons before mutation", async () => {
    const f = feature();
    f.request.mockResolvedValueOnce(archivedDocumentRetention());
    expect(
      await f.retention.reviewChange.execute(access, documentHeaderId, "archive", signal()),
    ).toMatchObject({ ok: false, failure: { code: "document_retention_action_unavailable" } });
    for (const reason of ["", "x\ny", "x".repeat(1001)])
      expect(
        await f.retention.change.execute(access, operation, state(), "archive", reason, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_document_retention_change" } });
    expect(
      await f.retention.change.execute(
        { ...access, companyId: documentId(2) as CompanyId },
        operation,
        state(),
        "archive",
        "Reason",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.retention.change.execute(
        access,
        operation,
        state(documentRetention(9999)),
        "archive",
        "Reason",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_retention_history_limit" } });
    expect(await f.retention.load.execute(access, "not-an-id", null, signal())).toMatchObject({
      ok: false,
    });
    expect(await f.retention.load.execute(access, documentHeaderId, "-1", signal())).toMatchObject({
      ok: false,
    });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  it.each([
    { id: documentId(22), version: 0 },
    { id: documentHeaderId, version: 1 },
  ])("rejects an unrelated lifecycle receipt %j", async (receipt) => {
    const f = feature();
    f.request.mockResolvedValueOnce(receipt);
    expect(
      await f.retention.change.execute(access, operation, state(), "archive", "Review", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves recent-auth rejection and cancellation of late reads and mutations", async () => {
    const f = feature();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "recent_authentication_required", detail: "PRIVATE" }),
    );
    expect(
      await f.retention.change.execute(access, operation, state(), "archive", "Review", signal()),
    ).toMatchObject({ ok: false, failure: { code: "recent_authentication_required" } });
    const readAbort = new AbortController();
    f.request.mockImplementationOnce(async () => {
      readAbort.abort();
      return documentRetention();
    });
    await expect(
      f.retention.load.execute(access, documentHeaderId, null, readAbort.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    const commandAbort = new AbortController();
    f.request.mockImplementationOnce(async () => {
      commandAbort.abort();
      return { id: documentHeaderId, version: 0 };
    });
    await expect(
      f.retention.change.execute(
        access,
        operation,
        state(),
        "archive",
        "Review",
        commandAbort.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
