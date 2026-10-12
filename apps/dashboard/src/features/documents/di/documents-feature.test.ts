import { describe, expect, it, vi } from "vitest";
import {
  documentBytes,
  documentCompanyId,
  documentEmployeeDirectory,
  documentEmployeeId,
  documentHeader,
  documentHeaderId,
  documentId,
  documentRevision,
  documentRevisionId,
} from "../../../../tests/fixtures/documents";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import type { CompanyId } from "../../../core/domain/identifiers";
import type {
  DocumentEmployeeDirectoryDto,
  DocumentRevisionDto,
} from "../data/models/document-dto";
import { createDocumentsFeature } from "./documents-feature";

const access = {
  companyId: documentCompanyId as CompanyId,
  permissions: ["documents.read", "people.profile.read"],
};
const signal = () => new AbortController().signal;
const search = { query: "", after: null };
const invalid = { ok: false, failure: { code: "invalid_response" } };

function first<T>(values: readonly T[]): T {
  const value = values[0];
  if (value === undefined) throw new Error("Expected a fixture item");
  return value;
}

function pendingResponse() {
  let resolve!: (value: unknown) => void;
  const promise = new Promise<unknown>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

describe("document read and download boundaries", () => {
  it("requires both read grants and validated routes before transport", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    for (const permissions of [
      [],
      ["documents.read"],
      ["people.profile.read"],
      ["documents.self.read"],
      ["documents.manage", "people.profile.manage"],
    ]) {
      const scope = { ...access, permissions };
      for (const result of await Promise.all([
        feature.loadEmployees.execute(scope, search, signal()),
        feature.loadFolder.execute(scope, documentEmployeeId, null, signal()),
        feature.loadReview.execute(scope, documentHeaderId, null, signal()),
        feature.loadRevision.execute(scope, documentHeaderId, documentRevisionId, signal()),
        feature.download.execute(scope, documentHeaderId, documentRevisionId, signal()),
      ]))
        expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const query of [
      { query: "x".repeat(121), after: null },
      { query: "\n", after: null },
      { query: "", after: "../other" },
    ])
      expect(await feature.loadEmployees.execute(access, query, signal())).toMatchObject({
        ok: false,
      });
    expect(await feature.loadFolder.execute(access, "../other", null, signal())).toMatchObject({
      ok: false,
    });
    expect(
      await feature.loadFolder.execute(access, documentEmployeeId, "bad", signal()),
    ).toMatchObject({ ok: false });
    for (const after of ["", "0", "01", "102", "Infinity"])
      expect(
        await feature.loadReview.execute(access, documentHeaderId, after, signal()),
      ).toMatchObject({ ok: false });
    expect(await feature.download.execute(access, documentHeaderId, "bad", signal())).toMatchObject(
      { ok: false },
    );
    expect(request).not.toHaveBeenCalled();
  });

  it("normalizes search and freezes the scoped display projection", async () => {
    const raw = documentEmployeeDirectory();
    raw.query = "North";
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const result = await feature.loadEmployees.execute(
      access,
      { query: " North ", after: null },
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        query: "North",
        employees: { items: [{ companyId: documentCompanyId, name: "North employee" }] },
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${documentCompanyId}/documents/employees?query=North&limit=20`,
    );
    if (!result.ok) return;
    first(raw.employees.items).name = "Changed";
    expect(result.value.employees.items[0]?.name).toBe("North employee");
    expect(Object.isFrozen(result.value.employees.items[0])).toBe(true);
    expect(Object.isFrozen(result.value.employees.items)).toBe(true);
  });

  it.each<[string, (raw: DocumentEmployeeDirectoryDto) => void]>([
    [
      "company",
      (raw) => {
        raw.companyId = documentId(2);
      },
    ],
    [
      "search",
      (raw) => {
        raw.query = "Other";
      },
    ],
    [
      "date",
      (raw) => {
        raw.asOf = "2026-02-30";
      },
    ],
    [
      "timezone",
      (raw) => {
        raw.timezone = "Invalid/Zone";
      },
    ],
    [
      "duplicate",
      (raw) => {
        raw.employees.items.push(first(raw.employees.items));
      },
    ],
    [
      "cursor",
      (raw) => {
        raw.employees.nextCursor = "EMP-001";
      },
    ],
  ])("rejects invalid directory %s", async (_, mutate) => {
    const raw = documentEmployeeDirectory();
    mutate(raw);
    const feature = createDocumentsFeature(
      { request: vi.fn().mockResolvedValue(raw) },
      { downloadBinary: vi.fn() },
    );
    expect(await feature.loadEmployees.execute(access, search, signal())).toMatchObject(invalid);
  });

  it("resolves direct navigation before listing the employee's documents", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(documentEmployeeDirectory())
      .mockResolvedValueOnce({ items: [documentHeader()], nextCursor: null });
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    expect(
      await feature.loadFolder.execute(access, documentEmployeeId.toUpperCase(), null, signal()),
    ).toMatchObject({
      ok: true,
      value: {
        employee: { id: documentEmployeeId },
        documents: { items: [{ id: documentHeaderId }] },
      },
    });
    expect(request.mock.calls[0]?.[0].path).toContain(`employmentId=${documentEmployeeId}`);
    expect(request.mock.calls[1]?.[0].path).toBe(
      `/api/v1/companies/${documentCompanyId}/documents?employmentId=${documentEmployeeId}&limit=20`,
    );
    const empty = documentEmployeeDirectory();
    empty.employees.items = [];
    request.mockReset().mockResolvedValue(empty);
    expect(
      await feature.loadFolder.execute(access, documentEmployeeId, null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("rejects a different employee, repeated headers and nonprogressing document cursors", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    for (const page of [
      { items: [{ ...documentHeader(), employmentId: documentId(12) }], nextCursor: null },
      { items: [documentHeader(), documentHeader()], nextCursor: null },
      { items: [documentHeader()], nextCursor: documentHeaderId },
    ]) {
      request
        .mockReset()
        .mockResolvedValueOnce(documentEmployeeDirectory())
        .mockResolvedValueOnce(page);
      expect(
        await feature.loadFolder.execute(access, documentEmployeeId, null, signal()),
      ).toMatchObject(invalid);
    }
    const foreign = documentEmployeeDirectory();
    first(foreign.employees.items).id = documentId(12);
    request.mockReset().mockResolvedValue(foreign);
    expect(
      await feature.loadFolder.execute(access, documentEmployeeId, null, signal()),
    ).toMatchObject(invalid);
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("keeps published metadata separate from a replacement revision still uploading", async () => {
    const header = { ...documentHeader(), revisionCount: 2 };
    const replacing = {
      ...documentRevision(),
      id: documentId(32),
      number: 2,
      status: "UPLOADING",
      uploadedBytes: 0,
      validatedAt: null,
      validationJobId: null,
      validationAttempts: 0,
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(header)
      .mockResolvedValueOnce({ items: [replacing, documentRevision()], nextCursor: null });
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const result = await feature.loadReview.execute(access, documentHeaderId, null, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        document: { currentRevisionId: documentRevisionId },
        revisions: {
          items: [
            { number: 2, status: "UPLOADING" },
            { number: 1, status: "READY" },
          ],
        },
      },
    });
    expect(request.mock.lastCall?.[0].path).toContain(
      `/documents/${documentHeaderId}/revisions?limit=20`,
    );
  });

  it.each<[string, (raw: DocumentRevisionDto) => void]>([
    [
      "foreign identity",
      (raw) => {
        raw.id = documentId(32);
      },
    ],
    [
      "wrong document",
      (raw) => {
        raw.documentId = documentId(22);
      },
    ],
    [
      "unsafe filename",
      (raw) => {
        raw.fileName = "../secret.pdf";
      },
    ],
    [
      "file type",
      (raw) => {
        raw.fileName = "file.png";
      },
    ],
    [
      "incomplete ready content",
      (raw) => {
        raw.uploadedBytes = 0;
      },
    ],
    [
      "missing approval",
      (raw) => {
        raw.validatedAt = null;
      },
    ],
    [
      "invalid digest",
      (raw) => {
        raw.sha256 = "bad";
      },
    ],
    [
      "excess bytes",
      (raw) => {
        raw.size = 101 * 1024 * 1024;
      },
    ],
    [
      "unsupported chunks",
      (raw) => {
        raw.chunkSize = 2048;
      },
    ],
  ])("does not expose a malformed revision: %s", async (_, mutate) => {
    const raw = documentRevision();
    mutate(raw);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const result = await feature.loadRevision.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      signal(),
    );
    expect(result).toMatchObject({
      ok: false,
      failure: {
        code:
          raw.documentId !== documentHeaderId ? "document_revision_not_found" : "invalid_response",
      },
    });
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("retains bounded validation history and rejects duplicate or missing attempts", async () => {
    const attempt = {
      jobId: documentId(41),
      number: 1,
      actorId: documentId(51),
      createdAt: "2026-10-01T00:01:00Z",
      reason: "Review content",
    };
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(documentRevision())
      .mockResolvedValueOnce([attempt]);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const result = await feature.loadRevision.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { attempts: [attempt] } });
    for (const attempts of [[attempt, attempt], [{ ...attempt, number: 2 }]]) {
      request.mockReset().mockResolvedValueOnce(documentRevision()).mockResolvedValueOnce(attempts);
      expect(
        await feature.loadRevision.execute(access, documentHeaderId, documentRevisionId, signal()),
      ).toMatchObject(invalid);
    }
  });

  it("cancels between dependent reads and preserves safe server failure classifications", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }));
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    expect(await feature.loadReview.execute(access, documentHeaderId, null, signal())).toEqual({
      ok: false,
      failure: { code: "mfa_required", fields: {}, parameters: {} },
    });
    const held = pendingResponse();
    request.mockReset().mockReturnValue(held.promise);
    const pending = new AbortController();
    const result = feature.loadFolder.execute(access, documentEmployeeId, null, pending.signal);
    pending.abort();
    held.resolve(documentEmployeeDirectory());
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(1);
  });

  it("reauthorizes ready content and clears borrowed bytes after native download handoff", async () => {
    const bytes = documentBytes();
    const saved: number[][] = [];
    const downloadBinary = vi
      .fn<FileRepository["downloadBinary"]>()
      .mockImplementation(async (file) => {
        saved.push(Array.from(new Uint8Array(first(file.parts))));
        return { ok: true, value: undefined };
      });
    const revision = documentRevision();
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(revision)
      .mockResolvedValueOnce({
        parts: [bytes.buffer],
        byteLength: bytes.byteLength,
        mediaType: revision.mediaType,
        etag: `"${revision.id}-${revision.sha256}"`,
      });
    const feature = createDocumentsFeature({ request }, { downloadBinary });
    expect(
      await feature.download.execute(access, documentHeaderId, documentRevisionId, signal()),
    ).toEqual({ ok: true, value: undefined });
    expect(saved).toEqual([Array.from(documentBytes())]);
    expect(bytes.every((byte) => byte === 0)).toBe(true);
    expect(request.mock.calls.map(([request]) => request.path)).toEqual([
      `/api/v1/companies/${documentCompanyId}/documents/revisions/${documentRevisionId}`,
      `/api/v1/companies/${documentCompanyId}/documents/revisions/${documentRevisionId}/content`,
    ]);
  });

  it.each([
    "UPLOADING",
    "VALIDATING",
    "VALIDATION_FAILED",
    "REJECTED",
    "CANCELLED",
    "EXPIRED",
    "RETIRED",
  ] as const)("does not download %s content", async (status) => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ ...documentRevision(), status });
    const downloadBinary = vi.fn();
    const feature = createDocumentsFeature({ request }, { downloadBinary });
    expect(
      await feature.download.execute(access, documentHeaderId, documentRevisionId, signal()),
    ).toMatchObject({ ok: false, failure: { code: "document_not_ready" } });
    expect(request).toHaveBeenCalledTimes(1);
    expect(downloadBinary).not.toHaveBeenCalled();
  });

  it("does not hand off denied or cancelled content", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(documentRevision())
      .mockRejectedValueOnce(new HttpResponseError(403, { code: "document_access_denied" }));
    const downloadBinary = vi.fn();
    const feature = createDocumentsFeature({ request }, { downloadBinary });
    expect(
      await feature.download.execute(access, documentHeaderId, documentRevisionId, signal()),
    ).toMatchObject({ ok: false, failure: { code: "document_access_denied" } });
    expect(downloadBinary).not.toHaveBeenCalled();
    const held = pendingResponse();
    request.mockReset().mockReturnValue(held.promise);
    const pending = new AbortController();
    const result = feature.download.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      pending.signal,
    );
    pending.abort();
    held.resolve(documentRevision());
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(downloadBinary).not.toHaveBeenCalled();
  });

  it("clears acquired content after mapping failure or cancellation during acquisition", async () => {
    for (const cancelled of [false, true]) {
      const bytes = documentBytes(),
        revision = documentRevision();
      const entered = pendingResponse(),
        held = pendingResponse();
      const request = vi
        .fn<HttpClient["request"]>()
        .mockResolvedValueOnce(revision)
        .mockImplementationOnce(() => {
          entered.resolve(undefined);
          return held.promise;
        });
      const downloadBinary = vi.fn();
      const feature = createDocumentsFeature({ request }, { downloadBinary });
      const pending = new AbortController();
      const result = feature.download.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        pending.signal,
      );
      await entered.promise;
      if (cancelled) pending.abort();
      held.resolve({
        parts: [bytes.buffer],
        byteLength: bytes.byteLength,
        mediaType: revision.mediaType,
        etag: cancelled ? `"${revision.id}-${revision.sha256}"` : '"another-revision"',
      });
      if (cancelled) await expect(result).rejects.toMatchObject({ name: "AbortError" });
      else expect(await result).toMatchObject(invalid);
      expect(downloadBinary).not.toHaveBeenCalled();
      expect(bytes.every((byte) => byte === 0)).toBe(true);
    }
  });

  it("clears content when the browser handoff fails", async () => {
    const bytes = documentBytes(),
      revision = documentRevision();
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(revision)
      .mockResolvedValueOnce({
        parts: [bytes.buffer],
        byteLength: bytes.byteLength,
        mediaType: revision.mediaType,
        etag: `"${revision.id}-${revision.sha256}"`,
      });
    const failure = {
      ok: false,
      failure: { code: "file_access_denied", fields: {}, parameters: {} },
    } as const;
    const feature = createDocumentsFeature(
      { request },
      { downloadBinary: vi.fn().mockResolvedValue(failure) },
    );
    expect(
      await feature.download.execute(access, documentHeaderId, documentRevisionId, signal()),
    ).toBe(failure);
    expect(bytes.every((byte) => byte === 0)).toBe(true);
  });
});
