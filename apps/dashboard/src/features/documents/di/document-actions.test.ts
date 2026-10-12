import { describe, expect, it, vi } from "vitest";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
  documentRevision,
  documentRevisionId,
} from "../../../../tests/fixtures/documents";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toDocumentRevision } from "../data/mappers/document-revision-mapper";
import type { DocumentRevisionDto } from "../data/models/document-dto";
import type { DocumentRevisionSnapshot } from "../domain/entities/document-revision-action";
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
const operation = documentId(80) as OperationId;
const signal = () => new AbortController().signal;
function uploading(): DocumentRevisionDto {
  return {
    ...documentRevision(),
    status: "UPLOADING",
    validatedAt: null,
    validationJobId: null,
    validationAttempts: 0,
    availableActions: ["VALIDATE", "CANCEL_UPLOAD"],
  };
}
const snapshot = (): DocumentRevisionSnapshot => toDocumentRevision(uploading(), access.companyId);

describe("document command boundaries", () => {
  it("binds a resumable command to the current file part and freezes its metadata", async () => {
    const raw = {
      ...uploading(),
      uploadedBytes: 0,
      availableActions: ["UPLOAD_CONTENT", "CANCEL_UPLOAD"],
      uploadContinuation: {
        operationId: documentId(81),
        offset: 0,
        size: uploading().size,
        sha256: uploading().sha256,
        attempts: 1,
        retryAt: null,
      },
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const reviewed = await feature.reviewAction.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      "cancel",
      signal(),
    );
    expect(reviewed).toMatchObject({
      ok: true,
      value: { uploadContinuation: raw.uploadContinuation },
    });
    raw.uploadContinuation.operationId = documentId(82);
    if (reviewed.ok) {
      expect(reviewed.value.uploadContinuation?.operationId).toBe(documentId(81));
      expect(Object.isFrozen(reviewed.value.uploadContinuation)).toBe(true);
    }
    for (const changed of [
      { ...raw, availableActions: ["CANCEL_UPLOAD"] },
      { ...raw, uploadContinuation: { ...raw.uploadContinuation, offset: 1 } },
      { ...raw, uploadContinuation: { ...raw.uploadContinuation, size: raw.size + 1 } },
      { ...raw, uploadContinuation: { ...raw.uploadContinuation, attempts: 8 } },
      { ...raw, uploadedBytes: raw.size },
      { ...raw, status: "CANCELLED" },
    ]) {
      request.mockResolvedValue(changed);
      expect(
        await feature.reviewAction.execute(
          access,
          documentHeaderId,
          documentRevisionId,
          "cancel",
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("ignores unsupported server capabilities without inventing an action", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ ...uploading(), availableActions: ["FUTURE_ACTION", "CANCEL_UPLOAD"] });
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    expect(
      await feature.reviewAction.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        "cancel",
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { availableActions: ["CANCEL_UPLOAD"] } });
    expect(
      await feature.reviewAction.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        "validate",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_action_unavailable" } });
  });
  it("requires read and management authority before acquiring a review", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    for (const permissions of [
      [],
      ["documents.read", "people.profile.read"],
      ["documents.manage", "people.profile.manage"],
      ["documents.read", "people.profile.read", "documents.manage"],
    ])
      expect(
        await feature.reviewAction.execute(
          { ...access, permissions },
          documentHeaderId,
          documentRevisionId,
          "validate",
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.reviewAction.execute(
        access,
        "../other",
        documentRevisionId,
        "cancel",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_revision_not_found" } });
    expect(request).not.toHaveBeenCalled();
  });

  it("requires the current server capability and exact parent, including for self-upload grants", async () => {
    const raw = uploading();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const scope = {
      ...access,
      permissions: ["documents.read", "people.profile.read", "documents.self.upload"],
    };
    const reviewed = await feature.reviewAction.execute(
      scope,
      documentHeaderId,
      documentRevisionId,
      "validate",
      signal(),
    );
    expect(reviewed).toMatchObject({
      ok: true,
      value: { availableActions: ["VALIDATE", "CANCEL_UPLOAD"] },
    });
    raw.availableActions.length = 0;
    if (reviewed.ok) {
      expect(reviewed.value.availableActions).toEqual(["VALIDATE", "CANCEL_UPLOAD"]);
      expect(Object.isFrozen(reviewed.value.availableActions)).toBe(true);
    }
    expect(
      await feature.reviewAction.execute(
        scope,
        documentHeaderId,
        documentRevisionId,
        "validate",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_action_unavailable" } });
    request.mockResolvedValue({ ...uploading(), documentId: documentId(22) });
    expect(
      await feature.reviewAction.execute(
        scope,
        documentHeaderId,
        documentRevisionId,
        "cancel",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_revision_not_found" } });
  });

  it("does not infer actions when an older API omits them and rejects duplicate capabilities", async () => {
    const { availableActions: _, ...raw } = uploading();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    expect(
      await feature.reviewAction.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        "cancel",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_action_unavailable" } });
    request.mockResolvedValue({ ...raw, availableActions: ["CANCEL_UPLOAD", "CANCEL_UPLOAD"] });
    expect(
      await feature.reviewAction.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        "cancel",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it.each(["validate", "cancelUpload"] as const)(
    "sends %s with the immutable observed version and operation",
    async (action) => {
      const revision = snapshot();
      const raw = { id: revision.id, version: revision.version + 1 };
      const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
      const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
      const pending = signal();
      const result = await feature[action].execute(
        access,
        operation,
        revision,
        "  Review content  ",
        pending,
      );
      expect(result).toEqual({ ok: true, value: raw });
      expect(request).toHaveBeenCalledExactlyOnceWith(
        {
          path: `/api/v1/companies/${documentCompanyId}/documents/revisions/${documentRevisionId}/${action === "validate" ? "validate" : "cancel"}`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion: revision.version, reason: "Review content" },
        },
        pending,
      );
      if (result.ok) {
        raw.version = 999;
        expect(result.value.version).toBe(revision.version + 1);
        expect(Object.isFrozen(result.value)).toBe(true);
      }
    },
  );

  it("allows explicit cancellation of a projected expired upload without inventing validation eligibility", async () => {
    const raw = {
      ...uploading(),
      status: "EXPIRED" as const,
      availableActions: ["CANCEL_UPLOAD" as const],
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const result = await feature.reviewAction.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      "cancel",
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { status: "EXPIRED" } });
    expect(
      await feature.reviewAction.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        "validate",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_action_unavailable" } });
  });

  it("rejects wrong scope, malformed commands and unavailable actions before transport", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    const revision = snapshot();
    for (const current of [
      { ...revision, companyId: documentId(2) as CompanyId },
      { ...revision, availableActions: [] },
      { ...revision, version: -1 },
      { ...revision, version: Number.MAX_SAFE_INTEGER },
      { ...revision, documentId: "bad" },
      { ...revision, id: "../other" },
    ])
      expect(
        await feature.validate.execute(access, operation, current, "Reason", signal()),
      ).toMatchObject({ ok: false });
    for (const reason of ["", "  ", "x".repeat(1001)])
      expect(
        await feature.cancelUpload.execute(access, operation, revision, reason, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_document_cancellation" } });
    expect(
      await feature.validate.execute(access, "bad" as OperationId, revision, "Reason", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_document_validation" } });
    expect(request).not.toHaveBeenCalled();
  });

  it.each([
    { id: documentId(32), version: 4 },
    { id: documentRevisionId, version: 9 },
    { id: documentRevisionId, version: -1 },
  ])("rejects a receipt not bound to the reviewed change: %j", async (raw) => {
    const feature = createDocumentsFeature(
      { request: vi.fn().mockResolvedValue(raw) },
      { downloadBinary: vi.fn() },
    );
    expect(
      await feature.cancelUpload.execute(access, operation, snapshot(), "Reason", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("preserves safe failure codes and cancellation across a late receipt", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(new HttpResponseError(409, { code: "stale_version", detail: "PRIVATE" }));
    const feature = createDocumentsFeature({ request }, { downloadBinary: vi.fn() });
    expect(
      await feature.validate.execute(access, operation, snapshot(), "Reason", signal()),
    ).toEqual({ ok: false, failure: { code: "stale_version", fields: {}, parameters: {} } });
    let resolve!: (value: unknown) => void;
    request.mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = new AbortController();
    const result = feature.cancelUpload.execute(
      access,
      operation,
      snapshot(),
      "Reason",
      pending.signal,
    );
    pending.abort();
    resolve({ id: documentRevisionId, version: snapshot().version + 1 });
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
  });
});
