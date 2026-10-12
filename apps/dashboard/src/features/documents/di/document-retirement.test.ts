import { describe, expect, it, vi } from "vitest";
import { documentFileInput } from "../../../../tests/fixtures/document-file-input";
import { documentRetirementReview } from "../../../../tests/fixtures/document-retirement";
import {
  documentCompanyId,
  documentHeaderId,
  documentId,
  documentRevisionId,
} from "../../../../tests/fixtures/documents";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toDocumentRetirementReview } from "../data/mappers/document-retirement-mapper";
import { createDocumentsFeature } from "./documents-feature";

const access = { companyId: documentCompanyId as CompanyId, permissions: ["documents.retention"] },
  operation = documentId(810) as OperationId;
const signal = () => new AbortController().signal;
const review = () =>
  toDocumentRetirementReview(
    documentRetirementReview(),
    access.companyId,
    documentHeaderId,
    documentRevisionId,
  );
function feature() {
  const request = vi.fn<HttpClient["request"]>();
  return {
    request,
    retention: createDocumentsFeature({ request }, { downloadBinary: vi.fn() }, documentFileInput())
      .retention,
  };
}

describe("document retirement boundaries", () => {
  it("requires the independent retention grant and valid references before I/O", async () => {
    const f = feature(),
      limited = {
        ...access,
        permissions: ["documents.read", "documents.manage", "people.profile.read"],
      };
    for (const result of await Promise.all([
      f.retention.reviewRetirement.execute(limited, documentHeaderId, documentRevisionId, signal()),
      f.retention.retireRevision.execute(
        limited,
        operation,
        review(),
        "Retention expired",
        signal(),
      ),
    ]))
      expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const [doc, rev] of [
      ["invalid", documentRevisionId],
      [documentHeaderId, "../retire"],
    ] as const)
      expect(await f.retention.reviewRetirement.execute(access, doc, rev, signal())).toMatchObject({
        ok: false,
        failure: { code: "document_revision_not_found" },
      });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("acquires only an immutable, resource-bound retention review", async () => {
    const f = feature(),
      raw = documentRetirementReview();
    f.request.mockResolvedValueOnce({ ...raw, fileName: "PRIVATE", storageKey: "PRIVATE" });
    const result = await f.retention.reviewRetirement.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      signal(),
    );
    expect(result).toEqual({ ok: true, value: review() });
    if (!result.ok) throw new Error("Expected review");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.retention)).toBe(true);
    expect(Object.isFrozen(result.value.retention.archive)).toBe(true);
    raw.retention.legalHold = true;
    if (raw.retention.archive) raw.retention.archive.retentionDays = 1;
    expect(result.value.retention.legalHold).toBe(false);
    expect(result.value.retention.archive?.retentionDays).toBe(365);
    expect(f.request.mock.calls.map(([r]) => r.path)).toEqual([
      `/api/v1/companies/${documentCompanyId}/documents/revisions/${documentRevisionId}/retirement`,
    ]);
  });
  it.each([
    "document_revision_not_ready",
    "document_not_archived",
    "document_legal_hold",
    "document_retention_indefinite",
    "document_retention_pending",
    "document_evidence_referenced",
    "document_retention_history_limit",
    "document_future_blocker",
  ])("preserves the server blocker %s and cannot submit it", async (blockingReason) => {
    const f = feature();
    f.request.mockResolvedValueOnce({ ...documentRetirementReview(), blockingReason });
    const result = await f.retention.reviewRetirement.execute(
      access,
      documentHeaderId,
      documentRevisionId,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error("Expected blocked review");
    expect(
      await f.retention.retireRevision.execute(access, operation, result.value, "Review", signal()),
    ).toMatchObject({ ok: false, failure: { code: blockingReason } });
    expect(f.request).toHaveBeenCalledTimes(1);
  });
  it.each([
    { revisionId: documentId(90) },
    { revisionNumber: 0 },
    { revisionNumber: 101 },
    { revisionVersion: Number.MAX_SAFE_INTEGER },
    { blockingReason: "" },
    { blockingReason: "PRIVATE\nDETAIL" },
    { status: "RETIRED" },
    { reviewedAt: "2024-01-01T00:00:00Z" },
    { retention: { ...documentRetirementReview().retention, documentId: documentId(22) } },
    { retention: { ...documentRetirementReview().retention, legalHold: true } },
    { retention: { ...documentRetirementReview().retention, version: null } },
    { retention: { ...documentRetirementReview().retention, version: 9999 } },
    { retention: { ...documentRetirementReview().retention, archive: null } },
  ])("rejects contradictory or unbound review data %j", async (override) => {
    const f = feature();
    f.request.mockResolvedValueOnce({ ...documentRetirementReview(), ...override });
    expect(
      await f.retention.reviewRetirement.execute(
        access,
        documentHeaderId,
        documentRevisionId,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("sends the original two versions and operation without refreshing the review", async () => {
    const f = feature(),
      receipt = { id: documentRevisionId, version: 4 };
    f.request.mockResolvedValue(receipt);
    for (let attempt = 0; attempt < 2; attempt++) {
      expect(
        await f.retention.retireRevision.execute(
          access,
          operation,
          review(),
          " Retention expired ",
          signal(),
        ),
      ).toEqual({ ok: true, value: receipt });
    }
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${documentCompanyId}/documents/revisions/${documentRevisionId}/retire`,
      method: "POST",
      operationId: operation,
      body: {
        expectedRevisionVersion: 3,
        expectedRetentionVersion: 0,
        reason: "Retention expired",
      },
    });
    expect(f.request.mock.calls[0]?.[0]).toEqual(f.request.mock.calls[1]?.[0]);
    expect(f.request).toHaveBeenCalledTimes(2);
  });
  it("rejects scope changes and invalid command input without sending a request", async () => {
    const f = feature();
    expect(
      await f.retention.retireRevision.execute(
        { ...access, companyId: documentId(2) as CompanyId },
        operation,
        review(),
        "Review",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const reason of ["", " ", "x\ny", "x".repeat(1001)])
      expect(
        await f.retention.retireRevision.execute(access, operation, review(), reason, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_document_retirement" } });
    for (const revisionVersion of [-1, 0.5, Number.MAX_SAFE_INTEGER])
      expect(
        await f.retention.retireRevision.execute(
          access,
          operation,
          { ...review(), revisionVersion },
          "Review",
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_document_retirement" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { id: documentHeaderId, version: 4 },
    { id: documentRevisionId, version: 5 },
  ])("does not acknowledge an unrelated retirement receipt %j", async (receipt) => {
    const f = feature();
    f.request.mockResolvedValueOnce(receipt);
    expect(
      await f.retention.retireRevision.execute(access, operation, review(), "Review", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves safe rejection codes and propagates cancellation of late results", async () => {
    const f = feature();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "recent_authentication_required", detail: "PRIVATE" }),
    );
    const rejected = await f.retention.retireRevision.execute(
      access,
      operation,
      review(),
      "Review",
      signal(),
    );
    expect(rejected).toMatchObject({
      ok: false,
      failure: { code: "recent_authentication_required" },
    });
    expect(JSON.stringify(rejected)).not.toContain("PRIVATE");
    for (const action of ["review", "retire"] as const) {
      const pending = new AbortController();
      f.request.mockImplementationOnce(async () => {
        pending.abort();
        return action === "review"
          ? documentRetirementReview()
          : { id: documentRevisionId, version: 4 };
      });
      await expect(
        action === "review"
          ? f.retention.reviewRetirement.execute(
              access,
              documentHeaderId,
              documentRevisionId,
              pending.signal,
            )
          : f.retention.retireRevision.execute(
              access,
              operation,
              review(),
              "Review",
              pending.signal,
            ),
      ).rejects.toMatchObject({ name: "AbortError" });
    }
  });
});
