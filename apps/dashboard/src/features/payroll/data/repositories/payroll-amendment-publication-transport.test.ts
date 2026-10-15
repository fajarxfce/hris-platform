import { describe, expect, it, vi } from "vitest";
import {
  amendmentPublicationDetails,
  amendmentPublicationId,
} from "../../../../../tests/fixtures/payroll-amendment-publications";
import { amendmentReviewId } from "../../../../../tests/fixtures/payroll-amendment-reviews";
import {
  amendmentCalculationId,
  amendmentId,
} from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollAmendmentPublicationDataSource } from "../datasources/http-payroll-amendment-publication-data-source";
import type { PayrollAmendmentPublicationDetailsDto } from "../models/payroll-amendment-publication-dto";
import { RemotePayrollAmendmentPublicationRepository } from "./remote-payroll-amendment-publication-repository";

const company = calculationCompany as CompanyId,
  operation = amendmentId(50) as OperationId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repo: new RemotePayrollAmendmentPublicationRepository(
      new HttpPayrollAmendmentPublicationDataSource(http),
    ),
  };
}
describe("amendment publication transport", () => {
  it.each(["PENDING", "PUBLISHED", "STOPPED", "EXHAUSTED"] as const)(
    "maps %s from its scoped job and amendment without mutating the response",
    async (status) => {
      const raw = amendmentPublicationDetails(status),
        f = fixture([raw]),
        result = await f.repo.list(company, amendmentId(), signal);
      expect(result.ok).toBe(true);
      if (!result.ok) throw new Error("Expected publication");
      expect(Object.isFrozen(result.value[0]?.job)).toBe(true);
      expect(Object.isFrozen(result.value[0]?.publication)).toBe(true);
      raw.amendment.employee.name = "Changed after acquisition";
      expect(result.value[0]?.amendment.employee.name).toBe("Amendment employee 1");
      expect(f.http.request).toHaveBeenCalledExactlyOnceWith(
        { path: `/api/v1/companies/${company}/payroll/amendments/${amendmentId()}/publications` },
        signal,
      );
    },
  );
  it.each<[(raw: PayrollAmendmentPublicationDetailsDto) => void]>([
    [
      (raw) => {
        raw.publication.amendmentId = amendmentId(2);
      },
    ],
    [
      (raw) => {
        raw.amendment.id = amendmentId(2);
      },
    ],
    [
      (raw) => {
        raw.job.jobId = amendmentPublicationId(2);
      },
    ],
    [
      (raw) => {
        raw.amendment.latestPublicationId = amendmentPublicationId(2);
      },
    ],
    [
      (raw) => {
        raw.publication.number = 2;
      },
    ],
    [
      (raw) => {
        raw.publication.amendmentVersion = 4;
      },
    ],
    [
      (raw) => {
        raw.publication.completedAt = "2026-10-01T01:00:04Z";
      },
    ],
    [
      (raw) => {
        raw.publication.completedBy = raw.publication.actorId;
      },
    ],
    [
      (raw) => {
        raw.publication.failureCode = "job_failed";
      },
    ],
    [
      (raw) => {
        raw.job.completedItems = 1;
      },
    ],
    [
      (raw) => {
        raw.job.status = "SUCCEEDED";
      },
    ],
    [
      (raw) => {
        Object.assign(raw.job, { totalItems: 2 });
      },
    ],
    [
      (raw) => {
        raw.job.version = -1;
      },
    ],
    [
      (raw) => {
        raw.availableActions = ["RECOVER"];
      },
    ],
    [
      (raw) => {
        raw.job.failureCode = "PRIVATE technical message";
      },
    ],
  ])("rejects invalid pending evidence %$", async (change) => {
    const raw = amendmentPublicationDetails();
    change(raw);
    expect(await fixture([raw]).repo.list(company, amendmentId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("rejects contradictory terminal outcomes and malformed publication sequences", async () => {
    for (const mutate of [
      (raw: PayrollAmendmentPublicationDetailsDto) => {
        raw.job.status = "RUNNING";
      },
      (raw: PayrollAmendmentPublicationDetailsDto) => {
        raw.publication.completedAt = null;
      },
      (raw: PayrollAmendmentPublicationDetailsDto) => {
        raw.publication.completedBy = amendmentId(2);
      },
      (raw: PayrollAmendmentPublicationDetailsDto) => {
        raw.publication.completedAt = "2026-10-01T01:00:03Z";
      },
    ]) {
      const raw = amendmentPublicationDetails("PUBLISHED");
      mutate(raw);
      expect((await fixture([raw]).repo.list(company, amendmentId(), signal)).ok).toBe(false);
    }
    const stopped = amendmentPublicationDetails("STOPPED");
    stopped.publication.failureCode = null;
    expect((await fixture([stopped]).repo.list(company, amendmentId(), signal)).ok).toBe(false);
    for (const raw of [
      [amendmentPublicationDetails(), amendmentPublicationDetails()],
      Array.from({ length: 9 }, () => amendmentPublicationDetails()),
    ])
      expect((await fixture(raw).repo.list(company, amendmentId(), signal)).ok).toBe(false);
  });
  it("keeps old stopped attempts while a new publication completes against a newer amendment", async () => {
    const first = amendmentPublicationDetails("STOPPED"),
      last = amendmentPublicationDetails("PUBLISHED");
    last.publication.id = amendmentPublicationId(2);
    last.publication.number = 2;
    last.publication.jobId = amendmentPublicationId(20);
    last.job.jobId = last.publication.jobId;
    last.publication.amendmentVersion = 5;
    last.amendment.version = 7;
    last.amendment.latestPublicationId = last.publication.id;
    first.amendment = structuredClone(last.amendment);
    expect((await fixture([first, last]).repo.list(company, amendmentId(), signal)).ok).toBe(true);
    first.amendment.version = 6;
    expect((await fixture([first, last]).repo.list(company, amendmentId(), signal)).ok).toBe(false);
  });
  for (const method of ["start", "recover"] as const) {
    const input = {
      expectedVersion: 3,
      publicationId: amendmentPublicationId(),
      reviewId: amendmentReviewId(),
      expectedReviewVersion: 1,
      expectedApprovalVersion: 1,
      calculationId: amendmentCalculationId,
      expectedJobVersion: 2,
      reason: "Reviewed",
      privateExtra: "private",
    };
    const invoke = (repo: RemotePayrollAmendmentPublicationRepository, ownedSignal = signal) =>
      method === "start"
        ? repo.start(company, amendmentId(), operation, input, ownedSignal)
        : repo.recover(
            company,
            amendmentId(),
            amendmentPublicationId(),
            operation,
            input,
            ownedSignal,
          );
    it(`${method} sends only its expected versions and maps the original amendment receipt`, async () => {
      const f = fixture({ id: amendmentId(), version: 4 });
      expect(await invoke(f.repo)).toEqual({ ok: true, value: { id: amendmentId(), version: 4 } });
      expect(f.http.request).toHaveBeenCalledExactlyOnceWith(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${amendmentId()}/publications/${amendmentPublicationId()}${method === "recover" ? "/recover" : ""}`,
          method: method === "start" ? "PUT" : "POST",
          operationId: operation,
          body:
            method === "start"
              ? {
                  expectedVersion: 3,
                  reviewId: amendmentReviewId(),
                  expectedReviewVersion: 1,
                  expectedApprovalVersion: 1,
                  calculationId: amendmentCalculationId,
                  reason: "Reviewed",
                }
              : { expectedVersion: 3, expectedJobVersion: 2, reason: "Reviewed" },
        },
        signal,
      );
      for (const wrong of [
        { id: amendmentPublicationId(), version: 4 },
        { id: amendmentId(), version: 3 },
        { id: amendmentId(), version: 5 },
      ])
        expect(await invoke(fixture(wrong).repo)).toMatchObject({
          ok: false,
          failure: { code: "invalid_response" },
        });
    });
    it(`${method} preserves safe server failure and rejects cancellation during acquisition`, async () => {
      const f = fixture({ id: amendmentId(), version: 4 });
      f.http.request.mockRejectedValueOnce(
        new HttpResponseError(409, {
          code: "payroll_amendment_sources_changed",
          detail: "PRIVATE",
        }),
      );
      const result = await invoke(f.repo);
      expect(result).toMatchObject({
        ok: false,
        failure: { code: "payroll_amendment_sources_changed" },
      });
      expect(JSON.stringify(result)).not.toContain("PRIVATE");
      const owner = new AbortController();
      f.http.request.mockImplementationOnce(async () => {
        owner.abort();
        return { id: amendmentId(), version: 4 };
      });
      await expect(invoke(f.repo, owner.signal)).rejects.toMatchObject({ name: "AbortError" });
      expect(f.http.request).toHaveBeenCalledTimes(2);
    });
  }
});
