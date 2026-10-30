import { createHash } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import { documentFileInput } from "../../../../tests/fixtures/document-file-input";
import {
  documentCompanyId,
  documentEmployeeDirectory,
  documentEmployeeId,
  documentHeader,
  documentHeaderId,
  documentId,
  documentRevision,
} from "../../../../tests/fixtures/documents";
import type { HttpClient, HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { BinaryFilePart } from "../../../core/domain/files/binary-file-input";
import type { CompanyId } from "../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../core/domain/result";
import { toDocument } from "../data/mappers/document-mapper";
import { toDocumentRevision } from "../data/mappers/document-revision-mapper";
import type { DocumentRevisionDto } from "../data/models/document-dto";
import type { DocumentUploadStartDto } from "../data/models/document-upload-dto";
import type { DocumentUploadFile, DocumentUploadReview } from "../domain/entities/document-upload";
import {
  documentUploadChunkBytes,
  documentUploadPartCount,
  prepareDocumentUpload,
} from "../domain/policies/document-upload-policy";
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
const signal = () => new AbortController().signal;
const fields = {
  title: " Employment record ",
  classification: "HR_ONLY" as const,
  reason: " Add signed record ",
};
const employee = {
  ...required(documentEmployeeDirectory().employees.items[0]),
  companyId: access.companyId,
};
const review: DocumentUploadReview = { kind: "new", employee };
const file = (size = 17): DocumentUploadFile => ({
  reference: { id: "local-file", name: "record.PDF", mediaType: "", byteLength: size },
  mediaType: "application/pdf",
  sha256: "a".repeat(64),
});
function value<T>(result: Result<T>): T {
  if (!result.ok) throw new Error(`Unexpected fixture failure: ${result.failure.code}`);
  return result.value;
}
function required<T>(value: T | undefined): T {
  if (value === undefined) throw new Error("Missing fixture value");
  return value;
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  return {
    promise: new Promise<T>((done) => {
      resolve = done;
    }),
    resolve: (value: T) => resolve(value),
  };
}
function fixture(size = documentUploadChunkBytes + 17) {
  const input = documentFileInput(),
    selected = file(size),
    parts: ArrayBuffer[] = [];
  const plan = value(
    prepareDocumentUpload(access, review, selected, fields, {
      documentId: documentId(100),
      revisionId: documentId(101),
      startOperation: documentId(102),
      chunkOperations: Array.from({ length: documentUploadPartCount(size) }, (_, i) =>
        documentId(103 + i),
      ),
    }),
  );
  const revision: DocumentRevisionDto = {
    ...documentRevision(),
    id: plan.revisionId,
    documentId: plan.documentId,
    fileName: selected.reference.name,
    mediaType: selected.mediaType,
    size,
    sha256: selected.sha256,
    status: "UPLOADING",
    uploadedBytes: 0,
    version: 0,
    validatedAt: null,
    validationJobId: null,
    validationAttempts: 0,
    availableActions: ["UPLOAD_CONTENT", "CANCEL_UPLOAD"],
  };
  vi.mocked(input.select).mockResolvedValue(success(selected.reference));
  vi.mocked(input.hash).mockResolvedValue(success(selected.sha256));
  vi.mocked(input.readPart).mockImplementation(async (_id, offset, length) => {
    const bytes = new Uint8Array(length).fill(offset / documentUploadChunkBytes + 1).buffer;
    parts.push(bytes);
    return success({
      bytes,
      sha256: createHash("sha256").update(new Uint8Array(bytes)).digest("hex"),
    });
  });
  let loseStart = false,
    loseChunk = false,
    reserve = false;
  const received: HttpRequest[] = [],
    receipts = new Map<string, { id: string; version: number }>();
  const request = vi.fn<HttpClient["request"]>().mockImplementation(async (request) => {
    received.push(request);
    if (request.method !== "POST") return structuredClone(revision);
    const operation = request.operationId ?? "";
    if (receipts.has(operation)) return receipts.get(operation);
    if (request.path.endsWith("/uploads")) {
      const body = request.body as DocumentUploadStartDto;
      const result = { id: body.revisionId, version: 0 };
      receipts.set(operation, result);
      if (loseStart) {
        loseStart = false;
        throw new TypeError("Lost response");
      }
      return result;
    }
    const binary = request.binaryBody;
    if (!binary) throw new Error("Missing file part");
    expect(Number(binary.headers["Upload-Offset"])).toBe(revision.uploadedBytes);
    expect(binary.bytes.byteLength).toBe(
      Math.min(documentUploadChunkBytes, size - revision.uploadedBytes),
    );
    expect(binary.headers["Upload-Checksum-Sha256"]).toBe(
      createHash("sha256").update(new Uint8Array(binary.bytes)).digest("hex"),
    );
    if (reserve) {
      reserve = false;
      revision.uploadContinuation = {
        operationId: operation,
        offset: revision.uploadedBytes,
        size: binary.bytes.byteLength,
        sha256: binary.headers["Upload-Checksum-Sha256"],
        attempts: 1,
        retryAt: null,
      };
      throw new HttpResponseError(503, { code: "storage_unavailable" });
    }
    if (revision.uploadContinuation)
      expect(operation).toBe(revision.uploadContinuation.operationId);
    revision.uploadedBytes += binary.bytes.byteLength;
    revision.version += 1;
    revision.uploadContinuation = null;
    revision.availableActions =
      revision.uploadedBytes === size
        ? ["VALIDATE", "CANCEL_UPLOAD"]
        : ["UPLOAD_CONTENT", "CANCEL_UPLOAD"];
    const result = { id: revision.id, version: revision.version };
    receipts.set(operation, result);
    if (loseChunk) {
      loseChunk = false;
      throw new TypeError("Lost response");
    }
    return result;
  });
  return {
    input,
    selected,
    plan,
    revision,
    parts,
    request,
    received,
    receipts,
    feature: createDocumentsFeature({ request }, { downloadBinary: vi.fn() }, input),
    loseStart: () => {
      loseStart = true;
    },
    loseChunk: () => {
      loseChunk = true;
    },
    reserve: () => {
      reserve = true;
    },
  };
}

describe("document upload preparation", () => {
  it("requires scoped administrative grants before reading a new upload or opening the picker", async () => {
    const f = fixture();
    for (const permissions of [
      [],
      ["documents.manage", "people.profile.manage"],
      ["documents.read", "people.profile.read", "documents.self.upload"],
    ]) {
      expect(
        await f.feature.reviewUpload.execute(
          { ...access, permissions },
          { kind: "new", employmentId: documentEmployeeId },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await f.feature.selectUploadFile.execute({ ...access, permissions }, review, signal()),
      ).toMatchObject({ ok: false });
    }
    expect(f.request).not.toHaveBeenCalled();
    expect(f.input.select).not.toHaveBeenCalled();
  });
  it("reads exact employee and document references without unrelated directory permissions", async () => {
    const f = fixture();
    f.request.mockResolvedValueOnce(documentEmployeeDirectory());
    expect(
      await f.feature.reviewUpload.execute(
        access,
        { kind: "new", employmentId: documentEmployeeId },
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { kind: "new", employee: { id: documentEmployeeId } } });
    expect(f.request.mock.calls[0]?.[0].path).toContain(`employmentId=${documentEmployeeId}`);
    f.request
      .mockResolvedValueOnce(documentHeader())
      .mockResolvedValueOnce(documentEmployeeDirectory());
    expect(
      await f.feature.reviewUpload.execute(
        access,
        { kind: "replacement", documentId: documentHeaderId },
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { document: { version: 2 } } });
    f.request
      .mockResolvedValueOnce(documentHeader())
      .mockResolvedValueOnce(documentEmployeeDirectory())
      .mockResolvedValueOnce(f.revision);
    expect(
      await f.feature.reviewUpload.execute(
        access,
        { kind: "resume", documentId: documentHeaderId, revisionId: f.revision.id },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_revision_not_found" } });
  });
  it("keeps replacement identity and observed version immutable while trimming new fields", () => {
    const f = fixture();
    expect(f.plan.start?.input).toMatchObject({
      title: "Employment record",
      reason: "Add signed record",
      expectedDocumentVersion: 0,
      classification: "HR_ONLY",
    });
    const existing = toDocument(documentHeader(), access.companyId);
    const replacement = value(
      prepareDocumentUpload(
        access,
        { kind: "replacement", employee, document: existing },
        file(),
        { ...fields, title: "Changed identity", classification: "RECEIPT" },
        {
          documentId: existing.id,
          revisionId: documentId(80),
          startOperation: documentId(81),
          chunkOperations: [documentId(82)],
        },
      ),
    );
    expect(replacement.start?.input).toMatchObject({
      documentId: existing.id,
      title: existing.title,
      classification: existing.classification,
      expectedDocumentVersion: 2,
    });
    expect(Object.isFrozen(replacement.start?.input)).toBe(true);
    expect(Object.isFrozen(replacement.chunkOperations)).toBe(true);
    expect(Object.isFrozen(replacement.file.reference)).toBe(true);
    expect(
      prepareDocumentUpload(access, review, file(), fields, {
        documentId: documentId(80),
        revisionId: documentId(81),
        startOperation: documentId(82),
        chunkOperations: [documentId(82)],
      }),
    ).toMatchObject({ ok: false });
    expect(
      prepareDocumentUpload(
        access,
        review,
        file(),
        { ...fields, reason: " " },
        {
          documentId: documentId(80),
          revisionId: documentId(81),
          startOperation: documentId(82),
          chunkOperations: [documentId(83)],
        },
      ),
    ).toMatchObject({ ok: false });
  });
  it.each(["bad.exe", "../record.pdf", "\nrecord.pdf", `${"x".repeat(181)}.pdf`])(
    "releases ineligible selected file %j before hashing",
    async (name) => {
      const f = fixture();
      vi.mocked(f.input.select).mockResolvedValue(success({ ...f.selected.reference, name }));
      expect(await f.feature.selectUploadFile.execute(access, review, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_document_file" },
      });
      expect(f.input.hash).not.toHaveBeenCalled();
      expect(f.input.release).toHaveBeenCalledExactlyOnceWith("local-file");
    },
  );
  it("reselects only identical content for resume and treats picker dismissal as successful null", async () => {
    const f = fixture();
    const resume: DocumentUploadReview = {
      kind: "resume",
      employee,
      document: { ...toDocument(documentHeader(), access.companyId), id: f.plan.documentId },
      revision: toDocumentRevision(f.revision, access.companyId),
    };
    vi.mocked(f.input.hash).mockResolvedValueOnce(success("b".repeat(64)));
    expect(await f.feature.selectUploadFile.execute(access, resume, signal())).toMatchObject({
      ok: false,
      failure: { code: "document_file_mismatch" },
    });
    expect(f.input.release).toHaveBeenCalledExactlyOnceWith("local-file");
    vi.mocked(f.input.select).mockResolvedValueOnce(success(null));
    expect(await f.feature.selectUploadFile.execute(access, resume, signal())).toEqual(
      success(null),
    );
    vi.mocked(f.input.select).mockResolvedValueOnce(
      success({ ...f.selected.reference, name: "local-copy.pdf" }),
    );
    expect(await f.feature.selectUploadFile.execute(access, resume, signal())).toMatchObject({
      ok: true,
      value: { reference: { name: "local-copy.pdf" } },
    });
  });
  it("releases acquired ownership after failed hashing or cancellation during late selection", async () => {
    const f = fixture();
    vi.mocked(f.input.hash).mockResolvedValueOnce(failed("file_read_failed"));
    expect(await f.feature.selectUploadFile.execute(access, review, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_read_failed" },
    });
    const held = deferred<Result<typeof f.selected.reference | null>>(),
      pending = new AbortController();
    vi.mocked(f.input.select).mockReturnValueOnce(held.promise);
    const result = f.feature.selectUploadFile.execute(access, review, pending.signal);
    pending.abort();
    held.resolve(success(f.selected.reference));
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(f.input.release).toHaveBeenCalledTimes(2);
  });
});

describe("bounded document delivery", () => {
  it("uploads a final short part, clears every buffer and leaves publication to validation", async () => {
    const f = fixture(2 * documentUploadChunkBytes + 17),
      progress = vi.fn();
    const result = await f.feature.uploadFile.execute(access, f.plan, progress, signal());
    expect(result).toMatchObject({
      ok: true,
      value: { status: "UPLOADING", uploadedBytes: f.selected.reference.byteLength },
    });
    const chunks = f.received.filter((request) => request.binaryBody);
    expect(chunks.map((request) => request.operationId)).toEqual(f.plan.chunkOperations);
    expect(chunks.map((request) => request.binaryBody?.bytes.byteLength)).toEqual([
      documentUploadChunkBytes,
      documentUploadChunkBytes,
      17,
    ]);
    expect(progress.mock.calls.map(([bytes]) => bytes)).toEqual([
      0,
      0,
      documentUploadChunkBytes,
      2 * documentUploadChunkBytes,
      f.selected.reference.byteLength,
    ]);
    expect(f.parts.every((part) => new Uint8Array(part).every((byte) => byte === 0))).toBe(true);
    expect(f.input.release).not.toHaveBeenCalled(); // The page still owns the selected reference.
    expect(f.request.mock.calls.every(([, signal]) => !signal.aborted)).toBe(true);
  });
  it.each(["start", "chunk"] as const)(
    "explicit retry recovers a lost %s response without duplicating committed bytes",
    async (lost) => {
      const f = fixture();
      if (lost === "start") f.loseStart();
      else f.loseChunk();
      expect(await f.feature.uploadFile.execute(access, f.plan, vi.fn(), signal())).toMatchObject({
        ok: false,
      });
      expect(await f.feature.uploadFile.execute(access, f.plan, vi.fn(), signal())).toMatchObject({
        ok: true,
        value: { uploadedBytes: f.selected.reference.byteLength },
      });
      const starts = f.received.filter((request) => request.path.endsWith("/uploads"));
      expect(starts).toHaveLength(2);
      expect(starts[1]).toEqual(starts[0]);
      expect(
        f.received
          .filter((request) => request.binaryBody)
          .map((request) => request.binaryBody?.headers["Upload-Offset"]),
      ).toEqual(["0", String(documentUploadChunkBytes)]);
      expect(f.receipts.size).toBe(3);
    },
  );
  it("recovers a reserved part after a new page creates different future operation IDs", async () => {
    const f = fixture();
    f.reserve();
    expect(await f.feature.uploadFile.execute(access, f.plan, vi.fn(), signal())).toMatchObject({
      ok: false,
      failure: { code: "storage_unavailable" },
    });
    const resume: DocumentUploadReview = {
      kind: "resume",
      employee,
      document: { ...toDocument(documentHeader(), access.companyId), id: f.plan.documentId },
      revision: toDocumentRevision(f.revision, access.companyId),
    };
    const newPlan = value(
      prepareDocumentUpload(access, resume, f.selected, fields, {
        documentId: f.plan.documentId,
        revisionId: f.plan.revisionId,
        startOperation: null,
        chunkOperations: [documentId(500), documentId(501)],
      }),
    );
    expect(newPlan.start).toBeNull();
    expect(await f.feature.uploadFile.execute(access, newPlan, vi.fn(), signal())).toMatchObject({
      ok: true,
    });
    expect(
      f.received.filter((request) => request.binaryBody).map((request) => request.operationId),
    ).toEqual([f.plan.chunkOperations[0], f.plan.chunkOperations[0], documentId(501)]);
    expect(f.received.filter((request) => request.path.endsWith("/uploads"))).toHaveLength(1);
  });
  it("rejects changed reserved bytes, closed revisions and revoked capabilities before upload I/O", async () => {
    const f = fixture();
    f.revision.uploadContinuation = {
      operationId: documentId(70),
      offset: 0,
      size: documentUploadChunkBytes,
      sha256: "b".repeat(64),
      attempts: 1,
      retryAt: null,
    };
    expect(
      await f.feature.uploadFile.execute(access, { ...f.plan, start: null }, vi.fn(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "document_file_mismatch" } });
    expect(f.parts.every((part) => new Uint8Array(part).every((byte) => byte === 0))).toBe(true);
    f.revision.uploadContinuation = null;
    f.revision.availableActions = ["CANCEL_UPLOAD"];
    expect(
      await f.feature.uploadFile.execute(access, { ...f.plan, start: null }, vi.fn(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "document_action_unavailable" } });
    f.revision.status = "CANCELLED";
    f.revision.availableActions = [];
    expect(
      await f.feature.uploadFile.execute(access, { ...f.plan, start: null }, vi.fn(), signal()),
    ).toMatchObject({ ok: false, failure: { code: "document_upload_closed" } });
    expect(f.received.some((request) => request.binaryBody)).toBe(false);
  });
  it.each([
    { id: documentId(999), version: 0 },
    { id: documentId(101), version: 2 },
  ])("rejects an unbound metadata receipt %j", async (receipt) => {
    const f = fixture();
    f.request.mockResolvedValueOnce(receipt);
    expect(await f.feature.uploadFile.execute(access, f.plan, vi.fn(), signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    expect(f.input.readPart).not.toHaveBeenCalled();
  });
  it("stops and clears bytes when a cancelled read returns late", async () => {
    const f = fixture(),
      held = deferred<Result<BinaryFilePart>>(),
      pending = new AbortController();
    const bytes = new Uint8Array(documentUploadChunkBytes).fill(5).buffer;
    vi.mocked(f.input.readPart).mockReturnValueOnce(held.promise);
    const result = f.feature.uploadFile.execute(access, f.plan, vi.fn(), pending.signal);
    await vi.waitFor(() => expect(f.input.readPart).toHaveBeenCalled());
    pending.abort();
    held.resolve(success({ bytes, sha256: "a".repeat(64) }));
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(new Uint8Array(bytes).every((byte) => byte === 0)).toBe(true);
    expect(f.received.some((request) => request.binaryBody)).toBe(false);
  });
  it("stops after cancellation during transport without publishing late progress", async () => {
    const f = fixture(),
      held = deferred<unknown>(),
      pending = new AbortController(),
      progress = vi.fn();
    const normal = required(f.request.getMockImplementation());
    f.request.mockImplementation((request, signal) =>
      request.binaryBody ? held.promise : normal(request, signal),
    );
    const result = f.feature.uploadFile.execute(access, f.plan, progress, pending.signal);
    await vi.waitFor(() => expect(f.parts).toHaveLength(1));
    pending.abort();
    held.resolve({ id: f.plan.revisionId, version: 1 });
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(progress.mock.calls.map(([bytes]) => bytes)).toEqual([0, 0]);
    expect(new Uint8Array(required(f.parts[0])).every((byte) => byte === 0)).toBe(true);
  });
});

describe("initial upload business metadata compatibility", () => {
  const identifiers = {
    documentId: documentId(100),
    revisionId: documentId(101),
    startOperation: documentId(102),
    chunkOperations: [documentId(103)],
  };
  it("retains the exact legacy body when metadata is omitted", () => {
    const plan = value(prepareDocumentUpload(access, review, file(), fields, identifiers));
    expect(plan.start?.input).not.toHaveProperty("metadata");
    expect(Object.keys(plan.start?.input ?? {})).toEqual([
      "documentId",
      "revisionId",
      "employmentId",
      "title",
      "classification",
      "expectedDocumentVersion",
      "fileName",
      "mediaType",
      "size",
      "sha256",
      "reason",
    ]);
  });
  it("owns normalized optional metadata before any file or HTTP acquisition", () => {
    const metadata = {
      category: "PASSPORT" as const,
      identityNumber: " P-123 ",
      issuingCountry: " au ",
      issuedOn: "2010-01-01",
      validUntil: "2020-01-01",
    };
    const plan = value(
      prepareDocumentUpload(
        access,
        { ...review, metadataSupported: true },
        file(),
        { ...fields, metadata },
        identifiers,
      ),
    );
    metadata.identityNumber = "Changed later";
    expect(plan.start?.input.metadata).toEqual({
      category: "PASSPORT",
      identityNumber: "P-123",
      issuingCountry: "AU",
      issuedOn: "2010-01-01",
      validUntil: "2020-01-01",
    });
    expect(Object.isFrozen(plan.start?.input.metadata)).toBe(true);
  });
  it("rejects an attempt to send new fields without advertised support", () => {
    const metadata = {
      category: "OTHER" as const,
      identityNumber: "",
      issuingCountry: "",
      issuedOn: "",
      validUntil: "",
    };
    expect(
      prepareDocumentUpload(access, review, file(), { ...fields, metadata }, identifiers),
    ).toMatchObject({ ok: false, failure: { code: "document_metadata_unsupported" } });
  });
  it("uses scoped employee discovery to advertise initial metadata support", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ ...documentEmployeeDirectory(), metadataSupported: true });
    const feature = createDocumentsFeature(
      { request },
      { downloadBinary: vi.fn() },
      documentFileInput(),
    );
    expect(
      await feature.reviewUpload.execute(
        access,
        { kind: "new", employmentId: documentEmployeeId },
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { kind: "new", metadataSupported: true } });
  });
});
