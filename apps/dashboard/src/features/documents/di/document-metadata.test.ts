import { describe, expect, it, vi } from "vitest";
import { documentFileInput } from "../../../../tests/fixtures/document-file-input";
import {
  documentMetadataFields,
  documentMetadataHeader,
  documentMetadataRevision,
} from "../../../../tests/fixtures/document-metadata";
import {
  documentCompanyId,
  documentHeader,
  documentHeaderId,
  documentId,
} from "../../../../tests/fixtures/documents";
import type { HttpClient } from "../../../core/data/http/http-client";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toDocument } from "../data/mappers/document-mapper";
import { createDocumentsFeature } from "./documents-feature";

const access = {
  companyId: documentCompanyId as CompanyId,
  permissions: [
    "documents.read",
    "people.profile.read",
    "documents.manage",
    "people.profile.manage",
  ],
};
const operation = documentId(801) as OperationId;
const signal = () => new AbortController().signal;
function feature() {
  const request = vi.fn<HttpClient["request"]>();
  return {
    request,
    actions: createDocumentsFeature({ request }, { downloadBinary: vi.fn() }, documentFileInput())
      .metadata,
  };
}

describe("document identity metadata boundaries", () => {
  it("loads and owns a normalized document projection including historical validity", async () => {
    const f = feature(),
      raw = documentMetadataHeader();
    f.request.mockResolvedValue(raw);
    const result = await f.actions.review.execute(access, documentHeaderId, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        metadataSupported: true,
        metadataVersion: 0,
        metadata: { validUntil: "2020-01-01" },
      },
    });
    if (!raw.metadata) throw new Error("Missing fixture metadata");
    raw.metadata.identityNumber = "Changed transport value";
    if (result.ok) {
      expect(result.value.metadata?.identityNumber).toBe("P-0");
      expect(Object.isFrozen(result.value.metadata)).toBe(true);
    }
  });
  it("does not send metadata operations to older endpoints", async () => {
    const f = feature();
    const {
      metadata: _,
      metadataVersion: __,
      metadataSupported: ___,
      ...legacy
    } = documentHeader();
    f.request.mockResolvedValue(legacy);
    expect(await f.actions.review.execute(access, documentHeaderId, signal())).toMatchObject({
      ok: false,
      failure: { code: "document_metadata_unsupported" },
    });
    expect(await f.actions.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "document_metadata_unsupported" },
    });
    expect(
      f.request.mock.calls.every(([request]) => request.path.endsWith(`/${documentHeaderId}`)),
    ).toBe(true);
    expect(
      await f.actions.save.execute(
        access,
        operation,
        toDocument(documentHeader(), access.companyId),
        documentMetadataFields,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_metadata_unsupported" } });
    expect(f.request).toHaveBeenCalledTimes(2);
  });
  it("requires original read and management grants before transport", async () => {
    const f = feature(),
      denied = { ...access, permissions: [] };
    const document = toDocument(documentMetadataHeader(), access.companyId);
    for (const result of await Promise.all([
      f.actions.review.execute(denied, documentHeaderId, signal()),
      f.actions.load.execute(denied, documentHeaderId, null, signal()),
      f.actions.save.execute(denied, operation, document, documentMetadataFields, signal()),
    ]))
      expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("pins metadata, version, reason and key in one mutation without uploading bytes", async () => {
    const f = feature();
    f.request.mockResolvedValue({ id: documentHeaderId, version: 3 });
    const document = toDocument(documentMetadataHeader(), access.companyId);
    expect(
      await f.actions.save.execute(access, operation, document, documentMetadataFields, signal()),
    ).toMatchObject({ ok: true, value: { id: documentHeaderId, version: 3 } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${documentCompanyId}/documents/${documentHeaderId}/metadata`,
      method: "PUT",
      operationId: operation,
      body: {
        expectedVersion: 2,
        metadata: {
          category: "WORK_PERMIT",
          identityNumber: "W-123",
          issuingCountry: "AU",
          issuedOn: "2010-01-01",
          validUntil: "2020-01-01",
        },
        reason: "Reviewed permit",
      },
    });
  });
  it.each([
    { id: documentId(802), version: 3 },
    { id: documentHeaderId, version: 4 },
  ])("rejects a receipt for a different command: %j", async (receipt) => {
    const f = feature();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.actions.save.execute(
        access,
        operation,
        toDocument(documentMetadataHeader(), access.companyId),
        documentMetadataFields,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it.each(["category", "number", "country", "date", "range", "capability", "revision"])(
    "rejects malformed metadata at the read boundary: %s",
    async (mode) => {
      const f = feature(),
        raw = documentMetadataHeader();
      const value: Record<string, unknown> = { ...raw.metadata };
      if (mode === "category") value.category = "UNKNOWN";
      if (mode === "number") value.identityNumber = "x".repeat(121);
      if (mode === "country") value.issuingCountry = "Australia";
      if (mode === "date") value.issuedOn = "2020-02-30";
      if (mode === "range") value.validUntil = "1900-01-01";
      f.request.mockResolvedValue({
        ...raw,
        metadata: value,
        ...(mode === "capability" ? { metadataSupported: false } : {}),
        ...(mode === "revision" ? { metadataVersion: 1000 } : {}),
      });
      expect(await f.actions.review.execute(access, documentHeaderId, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );
  it("paginates immutable history and keeps migrated provenance explicit", async () => {
    const f = feature();
    f.request.mockResolvedValueOnce(documentMetadataHeader(21)).mockResolvedValueOnce({
      items: Array.from({ length: 20 }, (_, i) => documentMetadataRevision(i)),
      nextCursor: "19",
    });
    const first = await f.actions.load.execute(access, documentHeaderId, null, signal());
    expect(first).toMatchObject({
      ok: true,
      value: { document: { metadataVersion: 21 }, history: { nextCursor: "19" } },
    });
    if (first.ok) {
      expect(first.value.history.items[0]).toMatchObject({ actorId: null, reason: null });
      expect(Object.isFrozen(first.value.history.items[0]?.metadata)).toBe(true);
    }
    f.request.mockResolvedValueOnce(documentMetadataHeader(21)).mockResolvedValueOnce({
      items: [documentMetadataRevision(20), documentMetadataRevision(21)],
      nextCursor: null,
    });
    expect(await f.actions.load.execute(access, documentHeaderId, "19", signal())).toMatchObject({
      ok: true,
      value: { history: { nextCursor: null, items: [{ version: 20 }, { version: 21 }] } },
    });
    expect(f.request.mock.calls[3]?.[0].path).toContain("limit=20&after=19");
  });
  it.each(["foreign", "gap", "duplicate", "cursor", "actor", "head"])(
    "rejects foreign or incoherent history: %s",
    async (mode) => {
      const f = feature();
      const row = documentMetadataRevision(0);
      if (mode === "foreign") row.documentId = documentId(99);
      if (mode === "gap") row.version = 1;
      if (mode === "head") row.metadata.identityNumber = "Different head";
      if (mode === "actor") {
        row.version = 1;
        row.actorId = null;
        row.reason = "Unattributed correction";
      }
      f.request.mockResolvedValueOnce(documentMetadataHeader()).mockResolvedValueOnce({
        items: mode === "duplicate" ? [row, row] : [row],
        nextCursor: mode === "cursor" ? "0" : null,
      });
      expect(await f.actions.load.execute(access, documentHeaderId, null, signal())).toMatchObject({
        ok: false,
        failure: { code: mode === "head" ? "document_metadata_changed" : "invalid_response" },
      });
    },
  );
  it.each(["-1", "00", "1000", "NaN", "1.1"])(
    "rejects unbounded cursor %s before transport",
    async (cursor) => {
      const f = feature();
      expect(
        await f.actions.load.execute(access, documentHeaderId, cursor, signal()),
      ).toMatchObject({ ok: false });
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it("does not return acquired metadata after cancellation", async () => {
    const f = feature(),
      controller = new AbortController();
    f.request.mockImplementation(async () => {
      controller.abort();
      return documentMetadataHeader();
    });
    await expect(
      f.actions.review.execute(access, documentHeaderId, controller.signal),
    ).rejects.toThrow();
  });
});
