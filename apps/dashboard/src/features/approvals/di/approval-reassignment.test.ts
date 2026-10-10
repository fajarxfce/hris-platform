import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ApprovalId, ApprovalRequest } from "../domain/entities/approval-request";
import { createApprovalsFeature } from "./approvals-feature";

const companyId = "10000000-abcd-4000-8000-000000000001" as CompanyId;
const account = (number: number) =>
  `20000000-abcd-4000-8000-${String(number).padStart(12, "0")}` as AccountId;
const id = "30000000-abcd-4000-8000-000000000001" as ApprovalId;
const operation = "90000000-abcd-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["approvals.manage"] };
const signal = () => new AbortController().signal;
const review: ApprovalRequest = {
  id,
  companyId,
  kind: "PAYROLL",
  resourceId: "40000000-abcd-4000-8000-000000000001",
  authorId: account(1),
  requesterId: account(2),
  excludedAccountIds: [account(3)],
  templateId: "50000000-abcd-4000-8000-000000000001",
  templateRevision: 0,
  stages: [{ assignees: [account(4)] }, { assignees: [] }],
  currentStep: 1,
  status: "BLOCKED",
  version: 5,
  submittedAt: "2026-10-01T00:00:00Z",
};
const selection = { assignees: [account(5)], reason: "Independent review" };

describe("approval reassignment boundary", () => {
  it("requires administration before reviewing or mutating and rejects foreign snapshots", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    const readOnly = { ...access, permissions: ["approvals.read", "payroll.review"] };
    expect(await feature.reviewReassignment.execute(readOnly, id, signal())).toMatchObject({
      ok: false,
      failure: { code: "access_denied" },
    });
    expect(
      await feature.reassign.execute(readOnly, operation, review, selection, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.reassign.execute(
        { ...access, companyId: account(10) as unknown as CompanyId },
        operation,
        review,
        selection,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await feature.reviewReassignment.execute(access, "../foreign", signal())).toMatchObject({
      ok: false,
      failure: { code: "approval_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
  });
  it("reviews the exact current request and declines already completed requests", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(review);
    const feature = createApprovalsFeature({ request });
    expect(
      await feature.reviewReassignment.execute(access, id.toUpperCase(), signal()),
    ).toMatchObject({ ok: true, value: { id, companyId, currentStep: 1, version: 5 } });
    expect(request.mock.lastCall?.[0].path).toBe(`/api/v1/companies/${companyId}/approvals/${id}`);
    for (const status of ["APPROVED", "REJECTED", "CANCELLED"] as const) {
      request.mockResolvedValueOnce({ ...review, status });
      expect(await feature.reviewReassignment.execute(access, id, signal())).toMatchObject({
        ok: false,
        failure: { code: "approval_changed" },
      });
      expect(
        await feature.reassign.execute(
          access,
          operation,
          { ...review, status },
          selection,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "approval_changed" } });
    }
    expect(request).toHaveBeenCalledTimes(4);
  });
  it("excludes all makers and beneficiaries while bounding counts, unique identities and reasons", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    for (const blocked of [review.authorId, review.requesterId, ...review.excludedAccountIds]) {
      expect(
        await feature.reassign.execute(
          access,
          operation,
          review,
          { ...selection, assignees: [blocked as AccountId] },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "approver_unavailable" } });
    }
    for (const invalid of [
      { assignees: [] },
      { assignees: Array.from({ length: 26 }, (_, index) => account(index + 10)) },
      { assignees: [account(5), account(5).toUpperCase() as AccountId] },
      { assignees: ["bad" as AccountId] },
      { reason: "  " },
      { reason: "x".repeat(1001) },
    ])
      expect(
        await feature.reassign.execute(
          access,
          operation,
          review,
          { ...selection, ...invalid },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_approval_reassignment" } });
    expect(
      await feature.reassign.execute(
        access,
        operation,
        { ...review, version: Number.MAX_SAFE_INTEGER },
        selection,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_approval_reassignment" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("sends only the reviewed version and selected accounts and can replay without reading a newer stage", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id, version: 6 });
    const feature = createApprovalsFeature({ request });
    const input = {
      ...selection,
      reason: "  Independent review  ",
      assignees: [account(5).toUpperCase() as AccountId],
      version: 0,
      id: "ignored",
    };
    for (let attempt = 0; attempt < 2; attempt++)
      expect(await feature.reassign.execute(access, operation, review, input, signal())).toEqual({
        ok: true,
        value: { id, version: 6 },
      });
    expect(request).toHaveBeenCalledTimes(2);
    for (const call of request.mock.calls)
      expect(call[0]).toEqual({
        path: `/api/v1/companies/${companyId}/approvals/${id}/reassign`,
        method: "POST",
        operationId: operation,
        body: { version: 5, assignees: [account(5)], reason: "Independent review" },
      });
    expect(review.currentStep).toBe(1);
    expect(input.version).toBe(0);
  });
  it("rejects mismatched receipts and retains safe API classifications and cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createApprovalsFeature({ request });
    for (const receipt of [
      { id, version: 5 },
      { id: account(5), version: 6 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.reassign.execute(access, operation, review, selection, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "approval_changed",
        fields: {},
        parameters: {},
        detail: "PRIVATE DETAILS",
      }),
    );
    const result = await feature.reassign.execute(access, operation, review, selection, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "approval_changed" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() =>
      feature.reassign.execute(access, operation, review, selection, cancelled.signal),
    ).toThrow();
    request.mockRejectedValueOnce(new DOMException("Cancelled", "AbortError"));
    await expect(feature.reviewReassignment.execute(access, id, signal())).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
