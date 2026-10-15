import { assert, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationDetails,
  calculationEmployee,
  calculationEmployeeId,
  calculationId,
  calculationJob,
  calculationPeriod,
  calculationReview,
  calculationRun,
  calculationStart,
} from "../../../../../tests/fixtures/payroll-calculations";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollCalculationDataSource } from "../datasources/http-payroll-calculation-data-source";
import type { PayrollRunDetailsDto } from "../models/payroll-calculation-dto";
import { RemotePayrollCalculationRepository } from "./remote-payroll-calculation-repository";

const company = calculationCompany as CompanyId,
  signal = new AbortController().signal,
  operation = calculationId as OperationId;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repo: new RemotePayrollCalculationRepository(new HttpPayrollCalculationDataSource(http)),
  };
}
describe("calculation transport and mapping", () => {
  it("keeps missing preparation sources explicit and reads no employee payload", async () => {
    const raw = calculationReview();
    raw.source = null;
    raw.policy = null;
    raw.availableActions = [];
    raw.blockingIssues = [
      { kind: "NOT_FOUND", code: "payroll_policy_not_found", fields: {}, parameters: {} },
    ];
    const { repo, http } = fixture(raw),
      result = await repo.review(company, calculationPeriod, signal);
    assert(result.ok);
    expect(result.value.source).toBeNull();
    expect(result.value.policy).toBeNull();
    expect(result.value.blockingIssues[0]).toMatchObject({
      kind: "NOT_FOUND",
      code: "payroll_policy_not_found",
    });
    expect(http.request).toHaveBeenCalledTimes(1);
    expect(http.request.mock.calls[0]?.[0].path).toContain("/calculation-review");
  });
  it("rejects a false preparation hint and a foreign period", async () => {
    const raw = calculationReview();
    raw.source = null;
    expect(await fixture(raw).repo.review(company, calculationPeriod, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    expect(
      await fixture(calculationReview()).repo.review(company, calculationId, signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("reads one bounded result page and keeps retained data immutable", async () => {
    const raw = calculationDetails(),
      { repo, http } = fixture(raw),
      result = await repo.run(company, calculationId, null, 50, signal);
    assert(result.ok);
    expect(http.request.mock.calls[0]?.[0].path).toContain(`/runs/${calculationId}?limit=50`);
    expect(Object.isFrozen(result.value.results.items[0]?.target)).toBe(true);
    assert(raw.results.items[0]);
    raw.results.items[0].target.employeeName = "Changed raw source";
    expect(result.value.results.items[0]?.target.employeeName).not.toBe("Changed raw source");
    expect(result.value.results.items[0]).not.toHaveProperty("facts");
  });
  it.each<[string, (raw: PayrollRunDetailsDto) => void]>([
    [
      "assessment revision without identity",
      (raw) => {
        assert(raw.results.items[0]);
        raw.results.items[0].target.previousAssessmentRevision = 1;
      },
    ],
    [
      "foreign run",
      (raw) => {
        raw.run.id = calculationPeriod;
      },
    ],
    [
      "wrong period",
      (raw) => {
        raw.period.id = calculationId;
      },
    ],
    [
      "impossible counts",
      (raw) => {
        raw.run.failed = 1;
      },
    ],
    [
      "job ahead of total",
      (raw) => {
        raw.job.completedItems = 999;
      },
    ],
    [
      "replaced attempt",
      (raw) => {
        assert(raw.attempts[0]);
        raw.attempts[0].jobId = calculationPeriod;
      },
    ],
    [
      "looping cursor",
      (raw) => {
        raw.results.nextCursor = "0";
      },
    ],
    [
      "unbounded page",
      (raw) => {
        const row = raw.results.items[0];
        assert(row);
        raw.results.items = Array.from({ length: 51 }, () => structuredClone(row));
      },
    ],
    [
      "unknown outcome job",
      (raw) => {
        assert(raw.results.items[0]);
        raw.results.items[0].jobId = calculationPeriod;
      },
    ],
    [
      "partial source identity",
      (raw) => {
        assert(raw.results.items[0]);
        raw.results.items[0].target.inputId = null;
      },
    ],
    [
      "invented successful amount",
      (raw) => {
        assert(raw.results.items[0]);
        raw.results.items[0].failure = {
          kind: "CONFLICT",
          code: "payroll_input_required",
          fields: {},
          parameters: {},
        };
      },
    ],
  ])("rejects %s inside the shared boundary", async (_, corrupt) => {
    const raw = calculationDetails();
    corrupt(raw);
    expect(await fixture(raw).repo.run(company, calculationId, null, 50, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("retains failed classification and signed exact employee output without widening requests", async () => {
    const { repo, http } = fixture(calculationEmployee()),
      result = await repo.employee(company, calculationId, calculationEmployeeId(), signal);
    assert(result.ok);
    expect(result.value.calculation?.tax.withheld).toBe("-0.50");
    expect(Object.isFrozen(result.value.evidence?.workDays[0])).toBe(true);
    expect(http.request.mock.calls[0]?.[0].path).toContain(
      `/runs/${calculationId}/employees/${calculationEmployeeId()}`,
    );
    http.request.mockResolvedValue(calculationEmployee("payroll_input_required"));
    const failure = await repo.employee(company, calculationId, calculationEmployeeId(), signal);
    assert(failure.ok);
    expect(failure.value.item.failure).toMatchObject({
      kind: "CONFLICT",
      code: "payroll_input_required",
    });
    expect(failure.value.calculation).toBeNull();
  });
  it("retains the exact previous assessment revision", async () => {
    const raw = calculationEmployee();
    raw.item.target.previousAssessmentId = calculationPeriod;
    raw.item.target.previousAssessmentRevision = 3;
    const result = await fixture(raw).repo.employee(
      company,
      calculationId,
      calculationEmployeeId(),
      signal,
    );
    assert(result.ok);
    expect(result.value.item.target.previousAssessmentId).toBe(calculationPeriod);
    expect(result.value.item.target.previousAssessmentRevision).toBe(3);
  });
  it("rejects mismatched employee totals and never turns invalid data into a zero result", async () => {
    const raw = calculationEmployee();
    assert(raw.calculation);
    raw.calculation.tax.withheld = "0";
    expect(
      await fixture(raw).repo.employee(company, calculationId, calculationEmployeeId(), signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(
      await fixture(calculationEmployee()).repo.employee(
        company,
        calculationId,
        calculationEmployeeId(2),
        signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("accepts a terminal failed job while its run still awaits cleanup", async () => {
    const raw = calculationDetails(
      calculationRun({ status: "PROCESSING", processed: 0, succeeded: 0, version: 0 }),
    );
    raw.job.status = "FAILED";
    raw.job.failureCode = "request_failed";
    raw.availableActions = ["RESUME", "ABANDON"];
    expect((await fixture(raw).repo.run(company, calculationId, null, 50, signal)).ok).toBe(true);
  });
  it("bounds complete history at twenty runs and rejects a duplicate sequence", async () => {
    const raw = { items: [calculationRun()], nextCursor: null };
    expect((await fixture(raw).repo.runs(company, calculationPeriod, signal)).ok).toBe(true);
    raw.items.push(calculationRun());
    expect(await fixture(raw).repo.runs(company, calculationPeriod, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("validates receipt identity and version without replacing the original command", async () => {
    const { repo, http } = fixture({ id: calculationId, version: 0 });
    expect(
      (await repo.start(company, calculationPeriod, operation, calculationStart(), signal)).ok,
    ).toBe(true);
    expect(http.request.mock.calls[0]?.[0]).toMatchObject({
      method: "POST",
      operationId: operation,
      body: calculationStart(),
    });
    http.request.mockResolvedValue({ id: calculationId, version: 2 });
    const input = {
      expectedVersion: 1,
      expectedPeriodVersion: 2,
      reason: "Correct reviewed inputs",
    };
    expect((await repo.abandon(company, calculationId, operation, input, signal)).ok).toBe(true);
    expect((await repo.resume(company, calculationId, operation, input, signal)).ok).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0].body).not.toHaveProperty("expectedPeriodVersion");
    http.request.mockResolvedValue({ id: calculationJob, version: 0 });
    expect(
      await repo.start(company, calculationPeriod, operation, calculationStart(), signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves safe failure fields and propagates cancellation after pending acquisition", async () => {
    const { repo, http } = fixture(null);
    http.request.mockRejectedValue(
      new HttpResponseError(409, {
        code: "stale_policy_version",
        parameters: { expected: "0" },
        fields: {},
        detail: "secret internal detail",
      }),
    );
    const result = await repo.review(company, calculationPeriod, signal);
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "stale_policy_version", parameters: { expected: "0" } },
    });
    expect(JSON.stringify(result)).not.toContain("secret");
    const abort = new AbortController();
    http.request.mockImplementation(async () => {
      abort.abort();
      return calculationReview();
    });
    await expect(repo.review(company, calculationPeriod, abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
