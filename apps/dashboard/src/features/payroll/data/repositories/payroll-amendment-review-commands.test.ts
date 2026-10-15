import { describe, expect, it, vi } from "vitest";
import { amendmentReviewId } from "../../../../../tests/fixtures/payroll-amendment-reviews";
import {
  amendmentCalculationId,
  amendmentId,
} from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollAmendmentReviewDataSource } from "../datasources/http-payroll-amendment-review-data-source";
import { RemotePayrollAmendmentReviewRepository } from "./remote-payroll-amendment-review-repository";

const company = calculationCompany as CompanyId,
  operation = amendmentId(90) as OperationId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repo: new RemotePayrollAmendmentReviewRepository(
      new HttpPayrollAmendmentReviewDataSource(http),
    ),
  };
}
describe("amendment review command transport", () => {
  for (const method of ["submit", "decide", "withdraw"] as const) {
    const input = {
      expectedVersion: 2,
      reason: "Reviewed",
      reviewId: amendmentReviewId(),
      calculationId: amendmentCalculationId,
      amendmentId: amendmentId(),
      expectedReviewVersion: 0,
      expectedApprovalVersion: 1,
      decision: "APPROVE" as const,
      privateExtra: "not a transport field",
    };
    const invoke = (repo: RemotePayrollAmendmentReviewRepository, ownedSignal = signal) =>
      repo[method](
        company,
        method === "submit" ? amendmentId() : amendmentReviewId(),
        operation,
        input,
        ownedSignal,
      );
    it(`${method} maps the amendment receipt and sends only its endpoint fields`, async () => {
      const f = fixture({ id: amendmentId().toUpperCase(), version: 3 }),
        result = await invoke(f.repo),
        body =
          method === "submit"
            ? { expectedVersion: 2, calculationId: amendmentCalculationId, reason: "Reviewed" }
            : {
                expectedVersion: 2,
                expectedReviewVersion: 0,
                expectedApprovalVersion: 1,
                reason: "Reviewed",
                ...(method === "decide" ? { decision: "APPROVE" } : {}),
              };
      expect(result).toEqual({ ok: true, value: { id: amendmentId(), version: 3 } });
      if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
      expect(f.http.request).toHaveBeenCalledExactlyOnceWith(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendmentId()}/reviews/${amendmentReviewId()}${method === "submit" ? "" : method === "decide" ? "/decisions" : "/withdraw"}`,
          method: method === "submit" ? "PUT" : "POST",
          operationId: operation,
          body,
        },
        signal,
      );
    });
    it.each([
      { id: amendmentReviewId(), version: 3 },
      { id: amendmentId(2), version: 3 },
      { id: amendmentId(), version: 2 },
      { id: amendmentId(), version: 4 },
      { id: amendmentId(), version: 3.5 },
    ])(`${method} rejects substituted receipts %j`, async (raw) => {
      expect(await invoke(fixture(raw).repo)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    });
    it(`${method} preserves mapped server failures and pending cancellation without retries`, async () => {
      const f = fixture({ id: amendmentId(), version: 3 });
      f.http.request.mockRejectedValueOnce(
        new HttpResponseError(409, {
          code: "payroll_amendment_review_not_current",
          detail: "PRIVATE diagnostic",
        }),
      );
      const result = await invoke(f.repo);
      expect(result).toMatchObject({
        ok: false,
        failure: { code: "payroll_amendment_review_not_current" },
      });
      expect(JSON.stringify(result)).not.toContain("PRIVATE");
      const owner = new AbortController();
      f.http.request.mockImplementationOnce(async () => {
        owner.abort();
        return { id: amendmentId(), version: 3 };
      });
      await expect(invoke(f.repo, owner.signal)).rejects.toMatchObject({ name: "AbortError" });
      expect(f.http.request).toHaveBeenCalledTimes(2);
    });
  }
});
