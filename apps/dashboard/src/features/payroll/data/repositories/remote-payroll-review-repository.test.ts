import { assert, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
  calculationRun,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  payrollReview,
  payrollReviewDetails,
  payrollReviewPreparation,
  reviewId,
  reviewSecond,
} from "../../../../../tests/fixtures/payroll-reviews";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollReviewDataSource } from "../datasources/http-payroll-review-data-source";
import type { PayrollReviewDetailsDto } from "../models/payroll-review-dto";
import { RemotePayrollReviewRepository } from "./remote-payroll-review-repository";

const company = calculationCompany as CompanyId,
  operation = reviewId as OperationId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return { http, repo: new RemotePayrollReviewRepository(new HttpPayrollReviewDataSource(http)) };
}
describe("payroll review transport", () => {
  it("keeps signed decimal totals, independent approval versions and minimal immutable names", async () => {
    const raw = payrollReviewDetails();
    raw.approval.version = 2;
    const { repo, http } = fixture(raw),
      result = await repo.get(company, reviewId, signal);
    assert(result.ok);
    expect(result.value.review.version).toBe(0);
    expect(result.value.approval.version).toBe(2);
    expect(result.value.review.totals.withheld).toBe("-0.50");
    expect(Object.isFrozen(result.value.accounts[0])).toBe(true);
    expect(Object.isFrozen(result.value.approval.stages[0])).toBe(true);
    assert(raw.accounts[0]);
    raw.accounts[0].displayName = "Changed source";
    expect(result.value.accounts[0]?.displayName).toBe("Payroll Preparer");
    expect(http.request).toHaveBeenCalledTimes(1);
  });
  it.each<[string, (raw: PayrollReviewDetailsDto) => void]>([
    [
      "foreign review",
      (r) => {
        r.review.id = reviewSecond;
      },
    ],
    [
      "foreign run",
      (r) => {
        r.review.runId = reviewSecond;
      },
    ],
    [
      "invented author",
      (r) => {
        r.approval.authorId = reviewSecond;
      },
    ],
    [
      "missing maker exclusion",
      (r) => {
        r.approval.excludedAccountIds = [];
      },
    ],
    [
      "contradictory status",
      (r) => {
        r.approval.status = "REJECTED";
      },
    ],
    [
      "missing history",
      (r) => {
        r.review.version = 2;
      },
    ],
    [
      "wrong totals",
      (r) => {
        r.review.totals.employeeCount = 2;
      },
    ],
    [
      "duplicated stage",
      (r) => {
        r.approval.stages[0] = [reviewSecond, reviewSecond];
      },
    ],
    [
      "unrelated name",
      (r) => {
        r.accounts.push({ id: reviewId, displayName: "Unrelated private name" });
      },
    ],
    [
      "withdrawal during finalization",
      (r) => {
        r.finalizationActive = true;
      },
    ],
    [
      "decision while blocked",
      (r) => {
        r.approval.status = "BLOCKED";
        r.availableActions = ["APPROVE"];
      },
    ],
  ])("rejects %s inside the shared error boundary", async (_, corrupt) => {
    const raw = payrollReviewDetails();
    corrupt(raw);
    expect(await fixture(raw).repo.get(company, reviewId, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("retains withdrawn history after the run advances without offering actions", async () => {
    const raw = payrollReviewDetails();
    raw.review.status = "WITHDRAWN";
    raw.review.version = 1;
    raw.approval.status = "CANCELLED";
    raw.approval.version = 1;
    assert(raw.changes[0]);
    raw.changes.push({
      ...raw.changes[0],
      revision: 1,
      action: "WITHDRAWN",
      status: "WITHDRAWN",
      approvalVersion: 1,
    });
    raw.run = calculationRun({ status: "ABANDONED", version: 2 });
    raw.availableActions = [];
    expect((await fixture(raw).repo.get(company, reviewId, signal)).ok).toBe(true);
  });
  it("represents incomplete totals without allowing submission", async () => {
    const raw = payrollReviewPreparation();
    raw.run = calculationRun({ status: "PROCESSING", version: 0, processed: 0, succeeded: 0 });
    raw.totals = {
      employeeCount: 0,
      taxableGross: "0",
      withheld: "0",
      takeHome: "0",
      currency: "IDR",
    };
    raw.availableActions = [];
    raw.blockingIssues = [
      { kind: "CONFLICT", code: "payroll_calculation_required", fields: {}, parameters: {} },
    ];
    const result = await fixture(raw).repo.prepare(company, calculationId, signal);
    assert(result.ok);
    expect(result.value.totals.employeeCount).toBe(0);
    raw.availableActions = ["SUBMIT"];
    expect((await fixture(raw).repo.prepare(company, calculationId, signal)).ok).toBe(false);
  });
  it("reads all eight bounded rounds without a duplicate or looping page", async () => {
    const raw = { items: [payrollReview()], nextCursor: null },
      { repo, http } = fixture(raw);
    expect((await repo.list(company, calculationId, signal)).ok).toBe(true);
    expect(http.request.mock.calls[0]?.[0].path).toContain("limit=8");
    raw.items.push(payrollReview());
    expect((await repo.list(company, calculationId, signal)).ok).toBe(false);
  });
  it("validates receipts and preserves exact operation/version fields", async () => {
    const { repo, http } = fixture({ id: reviewId, version: 1 }),
      input = {
        expectedVersion: 0,
        expectedApprovalVersion: 7,
        reason: "Reviewed",
        decision: "APPROVE" as const,
      };
    expect((await repo.decide(company, reviewId, operation, input, signal)).ok).toBe(true);
    expect(http.request.mock.calls[0]?.[0]).toMatchObject({
      method: "POST",
      operationId: operation,
      body: input,
    });
    expect(
      (
        await repo.withdraw(
          company,
          reviewId,
          operation,
          { expectedVersion: 0, expectedApprovalVersion: 7, reason: "Review again" },
          signal,
        )
      ).ok,
    ).toBe(true);
    http.request.mockResolvedValue({ id: reviewSecond, version: 0 });
    expect(
      (
        await repo.submit(
          company,
          calculationId,
          operation,
          { id: reviewId, expectedRunVersion: 1, reason: "Review" },
          signal,
        )
      ).ok,
    ).toBe(false);
  });
  it("keeps safe failures and propagates cancellation during pending acquisition", async () => {
    const { repo, http } = fixture(null);
    http.request.mockRejectedValue(
      new HttpResponseError(409, {
        code: "approval_changed",
        fields: {},
        parameters: { expected: "2" },
        detail: "private diagnostic",
      }),
    );
    const result = await repo.get(company, reviewId, signal);
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "approval_changed", parameters: { expected: "2" } },
    });
    expect(JSON.stringify(result)).not.toContain("private");
    const abort = new AbortController();
    http.request.mockImplementation(async () => {
      abort.abort();
      return payrollReviewDetails();
    });
    await expect(repo.get(company, reviewId, abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
