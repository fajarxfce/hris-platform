import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createApprovalsFeature } from "./approvals-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "30000000-0000-4000-8000-000000000001";
const actor = "20000000-0000-4000-8000-000000000001";
const access = { companyId: company, permissions: ["approvals.read"] };
const signal = () => new AbortController().signal;
const row = {
  id,
  kind: "LEAVE",
  resourceId: "40000000-0000-4000-8000-000000000001",
  authorId: actor,
  requesterId: null,
  templateId: "50000000-0000-4000-8000-000000000001",
  templateRevision: 0,
  stages: [{ assignees: [actor] }, { assignees: [] }],
  currentStep: 0,
  status: "PENDING",
  version: 0,
  submittedAt: "2026-10-01T09:30:00.123456Z",
  excludedAccountIds: [],
};

describe("approval read boundary", () => {
  it("checks inbox access and finite identifiers before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    expect(
      await feature.loadInbox.execute({ ...access, permissions: [] }, null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const after of ["", "../requests", "null"])
      expect(await feature.loadInbox.execute(access, after, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    expect(await feature.loadRequest.execute(access, "../secret", signal())).toMatchObject({
      ok: false,
      failure: { code: "approval_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
  });
  it("lets the backend authorize author/beneficiary detail reads without granting inbox access", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(row);
    const result = await createApprovalsFeature({ request }).loadRequest.execute(
      { ...access, permissions: [] },
      id.toUpperCase(),
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: { id, companyId: company, requesterId: null },
    });
    expect(request.mock.lastCall?.[0].path).toBe(`/api/v1/companies/${company}/approvals/${id}`);
  });
  it("detaches immutable assignment snapshots from DTO collections", async () => {
    const dto = structuredClone(row);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const result = await createApprovalsFeature({ request }).loadRequest.execute(
      access,
      id,
      signal(),
    );
    if (!result.ok) throw new Error("Expected a request");
    dto.stages[0]?.assignees.splice(0);
    expect(result.value.stages[0]?.assignees).toEqual([actor]);
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.stages)).toBe(true);
    expect(Object.isFrozen(result.value.stages[0]?.assignees)).toBe(true);
    expect(Object.isFrozen(result.value.excludedAccountIds)).toBe(true);
  });
  it("accepts bounded ascending pages and sends normalized cursors", async () => {
    const items = Array.from({ length: 20 }, (_, index) => ({
      ...row,
      id: `30000000-0000-4000-8000-${String(index + 2).padStart(12, "0")}`,
    }));
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items, nextCursor: items.at(-1)?.id });
    const result = await createApprovalsFeature({ request }).loadInbox.execute(
      access,
      id.toUpperCase(),
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { nextCursor: items.at(-1)?.id } });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/approvals?limit=20&after=${id}`,
    );
    if (!result.ok) throw new Error("Expected a page");
    expect(Object.isFrozen(result.value.items)).toBe(true);
  });
  it("rejects malformed, oversized, mismatched and repeated data without producing a domain entity", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    for (const dto of [
      { ...row, currentStep: 2 },
      { ...row, stages: [] },
      { ...row, stages: Array.from({ length: 9 }, () => ({ assignees: [] })) },
      { ...row, stages: [{ assignees: [actor, actor] }] },
      { ...row, excludedAccountIds: [actor, actor] },
      { ...row, version: Number.MAX_SAFE_INTEGER + 1 },
      { ...row, id: row.resourceId },
      { ...row, submittedAt: "2026-02-30T09:30:00Z" },
    ]) {
      request.mockResolvedValueOnce(dto);
      expect(await feature.loadRequest.execute(access, id, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    for (const page of [
      { items: [row, row], nextCursor: null },
      { items: [row], nextCursor: id },
      { items: [{ ...row, status: "APPROVED" }], nextCursor: null },
      { items: Array.from({ length: 21 }, () => row), nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(await feature.loadInbox.execute(access, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ items: [row], nextCursor: null });
    expect(await feature.loadInbox.execute(access, id, signal())).toMatchObject({ ok: false });
  });
  it("preserves safe failure codes and cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(404, {
        code: "approval_not_found",
        detail: "PRIVATE DATA",
        fields: {},
        parameters: {},
      }),
    );
    const load = createApprovalsFeature({ request }).loadRequest;
    const result = await load.execute(access, id, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "approval_not_found" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const pending = new AbortController();
    pending.abort();
    expect(() => load.execute(access, id, pending.signal)).toThrow();
    request.mockRejectedValueOnce(new DOMException("Cancelled", "AbortError"));
    await expect(load.execute(access, id, signal())).rejects.toMatchObject({ name: "AbortError" });
  });
});
