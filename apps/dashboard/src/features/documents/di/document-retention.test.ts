import { describe, expect, it, vi } from "vitest";
import { documentFileInput } from "../../../../tests/fixtures/document-file-input";
import { retentionPolicy, retentionPolicyId } from "../../../../tests/fixtures/document-retention";
import { documentCompanyId, documentId } from "../../../../tests/fixtures/documents";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toDocumentRetentionPolicy } from "../data/mappers/document-retention-mapper";
import type {
  DocumentRetentionPolicyDto,
  DocumentRetentionPolicyPageDto,
} from "../data/models/document-retention-dto";
import type { DocumentRetentionPolicySelection } from "../domain/entities/document-retention-policy";
import { createDocumentsFeature } from "./documents-feature";

const access = { companyId: documentCompanyId as CompanyId, permissions: ["documents.retention"] };
const operation = documentId(800) as OperationId;
const signal = () => new AbortController().signal;
const fields = { retentionDays: "365", reason: "Annual retention policy" };
function selection(version: number | null = 0): DocumentRetentionPolicySelection {
  return {
    companyId: access.companyId,
    classification: "PERSONAL",
    policy:
      version === null
        ? null
        : toDocumentRetentionPolicy(retentionPolicy(version), access.companyId),
  };
}
function feature(request = vi.fn<HttpClient["request"]>()) {
  return {
    request,
    useCases: createDocumentsFeature({ request }, { downloadBinary: vi.fn() }, documentFileInput())
      .retention,
  };
}

describe("retention policy boundaries", () => {
  it("requires a separate retention grant before every read and write", async () => {
    const f = feature(),
      unprivileged = {
        ...access,
        permissions: [
          "documents.read",
          "people.profile.read",
          "documents.manage",
          "people.profile.manage",
        ],
      };
    for (const result of await Promise.all([
      f.useCases.loadPolicies.execute(unprivileged, signal()),
      f.useCases.loadPolicy.execute(unprivileged, "PERSONAL", null, signal()),
      f.useCases.reviewPolicy.execute(unprivileged, "PERSONAL", signal()),
      f.useCases.savePolicy.execute(
        unprivileged,
        operation,
        selection(),
        retentionPolicyId,
        fields,
        signal(),
      ),
    ]))
      expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("loads immutable policy metadata without acquiring file or employee content", async () => {
    const raw = retentionPolicy(),
      f = feature();
    f.request.mockResolvedValue([raw]);
    const result = await f.useCases.loadPolicies.execute(access, signal());
    expect(result).toMatchObject({
      ok: true,
      value: [{ companyId: documentCompanyId, retentionDays: 365 }],
    });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${documentCompanyId}/documents/retention-policies`,
    });
    raw.retentionDays = 30;
    if (result.ok) {
      expect(result.value[0]?.retentionDays).toBe(365);
      expect(Object.isFrozen(result.value[0])).toBe(true);
      expect(Object.isFrozen(result.value)).toBe(true);
    }
  });
  it("represents an unconfigured category separately from a configured indefinite policy", async () => {
    const f = feature();
    f.request.mockResolvedValue([]);
    expect(await f.useCases.reviewPolicy.execute(access, "HR_ONLY", signal())).toMatchObject({
      ok: true,
      value: { classification: "HR_ONLY", policy: null },
    });
    expect(await f.useCases.loadPolicy.execute(access, "HR_ONLY", null, signal())).toMatchObject({
      ok: true,
      value: { history: { items: [], nextCursor: null } },
    });
    f.request.mockResolvedValue([{ ...retentionPolicy(), retentionDays: null }]);
    expect(await f.useCases.reviewPolicy.execute(access, "PERSONAL", signal())).toMatchObject({
      ok: true,
      value: { policy: { version: 0, retentionDays: null } },
    });
    expect(
      f.request.mock.calls.every(([request]) => request.path.endsWith("/retention-policies")),
    ).toBe(true);
  });
  it.each([
    [retentionPolicy(), retentionPolicy()],
    [retentionPolicy(), { ...retentionPolicy(), id: documentId(701) }],
    [{ ...retentionPolicy(), retentionDays: 0 }],
    [{ ...retentionPolicy(), retentionDays: 36501 }],
    [{ ...retentionPolicy(), version: 10000 }],
    [{ ...retentionPolicy(), reason: "\n" }],
  ])("rejects malformed or ambiguous policy headers: %j", async (...policies) => {
    const f = feature();
    f.request.mockResolvedValue(policies);
    expect(await f.useCases.loadPolicies.execute(access, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("rejects unsupported categories and unbounded history cursors before transport", async () => {
    const f = feature();
    for (const classification of ["", "OTHER", "../history"])
      expect(await f.useCases.reviewPolicy.execute(access, classification, signal())).toMatchObject(
        { ok: false },
      );
    for (const cursor of ["-1", "00", "10000", "1.5", "Infinity"])
      expect(
        await f.useCases.loadPolicy.execute(access, "PERSONAL", cursor, signal()),
      ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
  });
  it("paginates immutable history in ascending version order with a bounded cursor", async () => {
    const f = feature();
    f.request.mockResolvedValueOnce([retentionPolicy(21)]).mockResolvedValueOnce({
      items: Array.from({ length: 20 }, (_, index) => retentionPolicy(index)),
      nextCursor: "19",
    });
    const first = await f.useCases.loadPolicy.execute(access, "PERSONAL", null, signal());
    expect(first).toMatchObject({
      ok: true,
      value: { selection: { policy: { version: 21 } }, history: { nextCursor: "19" } },
    });
    f.request.mockResolvedValueOnce([retentionPolicy(21)]).mockResolvedValueOnce({
      items: [retentionPolicy(20), retentionPolicy(21)],
      nextCursor: null,
    });
    expect(await f.useCases.loadPolicy.execute(access, "PERSONAL", "19", signal())).toMatchObject({
      ok: true,
      value: { history: { items: [{ version: 20 }, { version: 21 }] } },
    });
    expect(f.request.mock.calls[3]?.[0].path).toBe(
      `/api/v1/companies/${documentCompanyId}/documents/retention-policies/${retentionPolicyId}/history?limit=20&after=19`,
    );
  });
  it.each<[string, (page: DocumentRetentionPolicyPageDto) => void]>([
    [
      "other policy",
      (page) => {
        if (page.items[0]) page.items[0].id = documentId(701);
      },
    ],
    [
      "classification",
      (page) => {
        if (page.items[0]) page.items[0].classification = "RECEIPT";
      },
    ],
    [
      "duplicate version",
      (page) => {
        page.items.push(retentionPolicy(0));
      },
    ],
    [
      "nonprogressing cursor",
      (page) => {
        page.nextCursor = "0";
      },
    ],
  ])("rejects history with %s", async (_name, mutate) => {
    const page = {
      items: [retentionPolicy(0)],
      nextCursor: null,
    } as DocumentRetentionPolicyPageDto;
    mutate(page);
    const f = feature();
    f.request.mockResolvedValueOnce([retentionPolicy(1)]).mockResolvedValueOnce(page);
    expect(await f.useCases.loadPolicy.execute(access, "PERSONAL", null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("reports a concurrent policy change without publishing a mixed current/history review or silently retrying", async () => {
    const f = feature();
    f.request
      .mockResolvedValueOnce([retentionPolicy(0)])
      .mockResolvedValueOnce({ items: [retentionPolicy(0), retentionPolicy(1)], nextCursor: null });
    expect(await f.useCases.loadPolicy.execute(access, "PERSONAL", null, signal())).toMatchObject({
      ok: false,
      failure: { code: "document_retention_review_changed" },
    });
    expect(f.request).toHaveBeenCalledTimes(2);
  });
  it.each([null, 0, 998])(
    "saves against the reviewed version %j with explicit indefinite retention",
    async (version) => {
      const f = feature(),
        pending = signal();
      f.request.mockResolvedValue({ id: retentionPolicyId, version: (version ?? -1) + 1 });
      expect(
        await f.useCases.savePolicy.execute(
          access,
          operation,
          selection(version),
          retentionPolicyId,
          { retentionDays: " ", reason: " Preserve records " },
          pending,
        ),
      ).toEqual({ ok: true, value: { id: retentionPolicyId, version: (version ?? -1) + 1 } });
      expect(f.request).toHaveBeenCalledExactlyOnceWith(
        {
          path: `/api/v1/companies/${documentCompanyId}/documents/retention-policies`,
          method: "POST",
          operationId: operation,
          body: {
            policyId: retentionPolicyId,
            classification: "PERSONAL",
            expectedVersion: version,
            retentionDays: null,
            reason: "Preserve records",
          },
        },
        pending,
      );
    },
  );
  it("rejects invalid duration, immutable identity and exhausted history before mutation", async () => {
    const f = feature();
    for (const days of ["0", "-1", "36501", "1.5", "1e2", "Infinity", "10,000"])
      expect(
        await f.useCases.savePolicy.execute(
          access,
          operation,
          selection(),
          retentionPolicyId,
          { ...fields, retentionDays: days },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_document_retention_policy" } });
    for (const reason of [" ", "x\ny", "x".repeat(1001)])
      expect(
        await f.useCases.savePolicy.execute(
          access,
          operation,
          selection(),
          retentionPolicyId,
          { ...fields, reason },
          signal(),
        ),
      ).toMatchObject({ ok: false });
    expect(
      await f.useCases.savePolicy.execute(
        access,
        operation,
        { ...selection(), classification: "HR_ONLY" },
        retentionPolicyId,
        fields,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await f.useCases.savePolicy.execute(
        access,
        operation,
        selection(),
        documentId(701),
        fields,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await f.useCases.savePolicy.execute(
        access,
        operation,
        selection(9999),
        retentionPolicyId,
        fields,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "document_retention_history_limit" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { id: documentId(701), version: 1 },
    { id: retentionPolicyId, version: 0 },
  ])("rejects an unrelated mutation receipt %j", async (receipt) => {
    const f = feature();
    f.request.mockResolvedValue(receipt);
    expect(
      await f.useCases.savePolicy.execute(
        access,
        operation,
        selection(),
        retentionPolicyId,
        fields,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves current-assurance failures and cancellation of a late policy read", async () => {
    const f = feature();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "recent_authentication_required", detail: "PRIVATE" }),
    );
    expect(
      await f.useCases.savePolicy.execute(
        access,
        operation,
        selection(),
        retentionPolicyId,
        fields,
        signal(),
      ),
    ).toEqual({
      ok: false,
      failure: { code: "recent_authentication_required", fields: {}, parameters: {} },
    });
    let resolve!: (value: DocumentRetentionPolicyDto[]) => void;
    f.request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const pending = new AbortController(),
      result = f.useCases.loadPolicies.execute(access, pending.signal);
    pending.abort();
    resolve([retentionPolicy()]);
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
  });
});
