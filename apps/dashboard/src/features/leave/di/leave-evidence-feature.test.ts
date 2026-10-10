import { describe, expect, it, vi } from "vitest";
import { leaveRecord } from "../../../../tests/fixtures/leave";
import { leaveEvidence } from "../../../../tests/fixtures/leave-evidence";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import type { CompanyId } from "../../../core/domain/identifiers";
import { failed, success } from "../../../core/domain/result";
import { toLeaveRequestDetails } from "../data/mappers/leave-request-details-mapper";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: [] };
function fixture() {
  const { attachment, bytes, etag } = leaveEvidence();
  const raw = { ...leaveRecord(), attachments: [attachment] };
  const detail = toLeaveRequestDetails(raw, companyId, null);
  const response = {
    parts: [Uint8Array.from(bytes).buffer],
    byteLength: bytes.byteLength,
    mediaType: attachment.mediaType,
    etag,
  };
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(response);
  const downloadBinary = vi
    .fn<FileRepository["downloadBinary"]>()
    .mockResolvedValue(success(undefined));
  const feature = createLeaveFeature({ request }, { downloadBinary });
  const pending = new AbortController();
  const run = () =>
    feature.downloadAttachment.execute(
      access,
      detail,
      attachment.revisionId.toUpperCase(),
      pending.signal,
    );
  return { attachment, detail, response, request, downloadBinary, feature, pending, run };
}
describe("leave evidence acquisition", () => {
  it("uses immutable request metadata and an authenticated bounded request before native download", async () => {
    const f = fixture();
    expect(await f.run()).toEqual(success(undefined));
    expect(f.request).toHaveBeenCalledExactlyOnceWith(
      {
        path: `/api/v1/companies/${companyId}/leave/requests/${f.detail.id}/attachments/${f.attachment.revisionId}/content`,
        timeoutMilliseconds: 60_000,
        response: {
          type: "binary",
          mediaType: "application/pdf",
          byteLength: f.attachment.size,
          etag: f.response.etag,
        },
      },
      f.pending.signal,
    );
    expect(f.downloadBinary).toHaveBeenCalledExactlyOnceWith(
      {
        name: f.attachment.fileName,
        mediaType: "application/pdf",
        byteLength: f.attachment.size,
        parts: f.response.parts,
      },
      f.pending.signal,
    );
    expect(Object.isFrozen(f.downloadBinary.mock.calls[0]?.[0].parts)).toBe(true);
    f.response.parts.splice(0);
    expect(f.downloadBinary.mock.calls[0]?.[0].parts).toHaveLength(1);
  });
  it("rejects foreign company scope and unknown revisions before I/O", async () => {
    const f = fixture();
    expect(
      await f.feature.downloadAttachment.execute(
        { ...access, companyId: "10000000-0000-4000-8000-000000000002" as CompanyId },
        f.detail,
        f.attachment.revisionId,
        f.pending.signal,
      ),
    ).toEqual(failed("access_denied"));
    for (const revision of ["invalid", f.attachment.documentId])
      expect(
        await f.feature.downloadAttachment.execute(access, f.detail, revision, f.pending.signal),
      ).toEqual(failed("leave_attachment_not_found"));
    expect(f.request).not.toHaveBeenCalled();
    expect(f.downloadBinary).not.toHaveBeenCalled();
  });
  it.each([
    { size: 0 },
    { size: 100 * 1024 * 1024 + 1 },
    { size: 1.5 },
    { mediaType: "text/html" },
    { sha256: "invalid" },
    { fileName: " " },
    { fileName: "a".repeat(181) },
    { fileName: "../evidence.pdf" },
    { fileName: "folder\\evidence.pdf" },
    { fileName: "evidence\n.pdf" },
  ])("rejects unsafe submitted metadata %j before I/O", async (change) => {
    const f = fixture();
    const detail = { ...f.detail, attachments: [{ ...f.attachment, ...change }] };
    expect(
      await f.feature.downloadAttachment.execute(
        access,
        detail,
        f.attachment.revisionId,
        f.pending.signal,
      ),
    ).toEqual(failed("leave_attachment_unavailable"));
    expect(f.request).not.toHaveBeenCalled();
    expect(f.downloadBinary).not.toHaveBeenCalled();
  });
  it.each(["etag", "mime", "length", "partial", "empty", "oversized-part"] as const)(
    "rejects %s response mismatch before file handoff",
    async (kind) => {
      const f = fixture();
      const wrong = {
        etag: { ...f.response, etag: '"different-revision"' },
        mime: { ...f.response, mediaType: "text/html" },
        length: { ...f.response, byteLength: f.response.byteLength + 1 },
        partial: { ...f.response, parts: [new ArrayBuffer(1)] },
        empty: { ...f.response, parts: [] },
        "oversized-part": { ...f.response, parts: [new ArrayBuffer(65537)] },
      }[kind];
      f.request.mockResolvedValue(wrong);
      expect(await f.run()).toEqual(failed("invalid_response"));
      expect(f.downloadBinary).not.toHaveBeenCalled();
    },
  );
  it("retains safe server and native failures without exposing private diagnostics", async () => {
    const f = fixture();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(404, {
        code: "leave_attachment_not_found",
        detail: "PRIVATE STORAGE PATH",
        fields: {},
        parameters: {},
      }),
    );
    expect(await f.run()).toEqual(failed("leave_attachment_not_found"));
    expect(f.downloadBinary).not.toHaveBeenCalled();
    const native = failed("file_download_unavailable");
    f.downloadBinary.mockResolvedValueOnce(native);
    expect(await f.run()).toBe(native);
  });
  it("propagates cancellation before acquisition and after a late response without native download", async () => {
    const f = fixture();
    f.pending.abort();
    await expect(f.run()).rejects.toMatchObject({ name: "AbortError" });
    expect(f.request).not.toHaveBeenCalled();
    const late = fixture();
    late.request.mockImplementationOnce(async () => {
      late.pending.abort();
      return late.response;
    });
    await expect(late.run()).rejects.toMatchObject({ name: "AbortError" });
    expect(late.downloadBinary).not.toHaveBeenCalled();
  });
});
