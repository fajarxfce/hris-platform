import { assert, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
  calculationRun,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  finalizationId,
  payrollFinalization,
  payrollFinalizationDetails,
  payrollFinalizationPreparation,
} from "../../../../../tests/fixtures/payroll-finalizations";
import { reviewId } from "../../../../../tests/fixtures/payroll-reviews";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollPublicationSearch } from "../../domain/entities/payroll-publication-search";
import { HttpPayrollFinalizationDataSource } from "../datasources/http-payroll-finalization-data-source";
import type {
  PayrollFinalizationDetailsDto,
  PayrollFinalizationPreparationDto,
} from "../models/payroll-finalization-dto";
import { RemotePayrollFinalizationRepository } from "./remote-payroll-finalization-repository";

const company = calculationCompany as CompanyId,
  operation = finalizationId as OperationId,
  signal = new AbortController().signal,
  search: PayrollPublicationSearch = {
    from: "2026-09",
    until: "2026-09",
    status: "CALCULATED",
    after: null,
  };
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repo: new RemotePayrollFinalizationRepository(new HttpPayrollFinalizationDataSource(http)),
  };
}
describe("publication transport and mapping", () => {
  it("keeps signed decimal totals and independently captured versions immutable", async () => {
    const raw = payrollFinalizationPreparation(),
      { repo, http } = fixture(raw),
      result = await repo.prepare(company, calculationId, signal);
    assert(result.ok);
    expect(result.value.totals.withheld).toBe("-0.50");
    expect(result.value.latestReview?.version).toBe(2);
    expect(result.value.approvalVersion).toBe(3);
    expect(Object.isFrozen(result.value.latestReview?.totals)).toBe(true);
    raw.totals.takeHome = "0";
    expect(result.value.totals.takeHome).toBe("10000000.75");
    expect(http.request).toHaveBeenCalledTimes(1);
  });
  it.each<[string, (raw: PayrollFinalizationPreparationDto) => void]>([
    [
      "wrong run",
      (r) => {
        r.run.id = finalizationId;
      },
    ],
    [
      "foreign review",
      (r) => {
        if (r.latestReview) r.latestReview.runId = finalizationId;
      },
    ],
    [
      "unapproved action",
      (r) => {
        if (r.latestReview) r.latestReview.status = "PENDING";
      },
    ],
    [
      "obsolete run version",
      (r) => {
        if (r.latestReview) r.latestReview.runVersion = 0;
      },
    ],
    [
      "missing approval",
      (r) => {
        r.approvalVersion = null;
      },
    ],
    [
      "incomplete totals",
      (r) => {
        r.totals.employeeCount = 0;
      },
    ],
    [
      "active publication",
      (r) => {
        r.latestFinalization = payrollFinalization();
        r.latestJobStatus = "RUNNING";
      },
    ],
    [
      "exhausted attempts",
      (r) => {
        r.latestFinalization = payrollFinalization({ number: 8 });
        r.latestJobStatus = "FAILED";
      },
    ],
    [
      "contradictory publication",
      (r) => {
        r.latestFinalization = payrollFinalization({ publishedAt: "2026-10-01T01:00:03Z" });
        r.latestJobStatus = "FAILED";
      },
    ],
  ])("rejects %s inside the safe boundary", async (_, corrupt) => {
    const raw = payrollFinalizationPreparation();
    corrupt(raw);
    expect(await fixture(raw).repo.prepare(company, calculationId, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("distinguishes an old cancelled attempt from a later successful publication", async () => {
    const raw = payrollFinalizationDetails("CANCELLED");
    raw.run = calculationRun({ status: "FINALIZED", version: 2, finalizationId: reviewId });
    const result = await fixture(raw).repo.get(company, finalizationId, signal);
    assert(result.ok);
    expect(result.value.finalization.publishedAt).toBeNull();
    expect(result.value.job.status).toBe("CANCELLED");
    expect(
      (
        await fixture(payrollFinalizationDetails("SUCCEEDED")).repo.get(
          company,
          finalizationId,
          signal,
        )
      ).ok,
    ).toBe(true);
  });
  it.each<[string, (raw: PayrollFinalizationDetailsDto) => void]>([
    [
      "foreign publication",
      (r) => {
        r.finalization.id = reviewId;
      },
    ],
    [
      "foreign job",
      (r) => {
        r.job.jobId = reviewId;
      },
    ],
    [
      "missing publication evidence",
      (r) => {
        r.finalization.publishedAt = null;
      },
    ],
    [
      "wrong finalized reference",
      (r) => {
        r.run.finalizationId = reviewId;
      },
    ],
    [
      "unpublished run",
      (r) => {
        r.run = calculationRun();
      },
    ],
    [
      "incomplete job",
      (r) => {
        r.job.completedItems = 0;
      },
    ],
  ])("does not display %s as a successful publication", async (_, corrupt) => {
    const raw = payrollFinalizationDetails("SUCCEEDED");
    corrupt(raw);
    expect(await fixture(raw).repo.get(company, finalizationId, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("enforces directory filters, ascending positions and the finite page contract", async () => {
    const items = Array.from({ length: 50 }, (_, i) =>
      calculationRun({ id: `ef300000-0000-4000-8000-${String(i + 1).padStart(12, "0")}` }),
    );
    const raw = { items, nextCursor: items.at(-1)?.id ?? null },
      { repo, http } = fixture(raw);
    const result = await repo.runs(company, search, signal);
    assert(result.ok);
    expect(result.value.items).toHaveLength(50);
    expect(http.request.mock.calls[0]?.[0].path).toContain("limit=50");
    raw.nextCursor = finalizationId;
    expect((await repo.runs(company, search, signal)).ok).toBe(false);
    raw.nextCursor = null;
    raw.items = [calculationRun({ earningsMonth: "2026-10" })];
    expect((await repo.runs(company, search, signal)).ok).toBe(false);
    raw.items = [calculationRun(), calculationRun()];
    expect((await repo.runs(company, search, signal)).ok).toBe(false);
  });
  it("reads complete bounded attempt history without accepting duplicate jobs or another run", async () => {
    const raw = { items: [payrollFinalization()], nextCursor: null },
      { repo, http } = fixture(raw);
    expect((await repo.list(company, calculationId, signal)).ok).toBe(true);
    expect(http.request.mock.calls[0]?.[0].path).toContain("limit=8");
    raw.items.push(payrollFinalization({ id: reviewId, number: 2 }));
    expect((await repo.list(company, calculationId, signal)).ok).toBe(false);
    raw.items = [payrollFinalization({ runId: reviewId })];
    expect((await repo.list(company, calculationId, signal)).ok).toBe(false);
  });
  it("retains exact commands and validates the original receipt", async () => {
    const { repo, http } = fixture({ id: finalizationId, version: 0 }),
      input = {
        id: finalizationId,
        reviewId,
        expectedRunVersion: 1,
        expectedReviewVersion: 2,
        expectedApprovalVersion: 7,
        reason: "Publish reviewed payroll",
      };
    expect((await repo.start(company, calculationId, operation, input, signal)).ok).toBe(true);
    expect(http.request.mock.calls[0]?.[0]).toMatchObject({
      method: "POST",
      operationId: operation,
      body: input,
    });
    http.request.mockResolvedValue({ id: reviewId, version: 0 });
    expect((await repo.start(company, calculationId, operation, input, signal)).ok).toBe(false);
    http.request.mockResolvedValue({ id: finalizationId, version: 1 });
    expect((await repo.start(company, calculationId, operation, input, signal)).ok).toBe(false);
  });
  it("preserves safe failure codes and cancellation without technical messages or retries", async () => {
    const { repo, http } = fixture(null);
    http.request.mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "approval_changed",
        fields: {},
        parameters: {},
        detail: "PRIVATE TECHNICAL DIAGNOSTIC",
      }),
    );
    const failed = await repo.prepare(company, calculationId, signal);
    expect(failed).toMatchObject({ ok: false, failure: { code: "approval_changed" } });
    expect(JSON.stringify(failed)).not.toContain("PRIVATE");
    expect(http.request).toHaveBeenCalledTimes(1);
    const abort = new AbortController();
    http.request.mockImplementationOnce(async () => {
      abort.abort();
      return payrollFinalizationPreparation();
    });
    await expect(repo.prepare(company, calculationId, abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
