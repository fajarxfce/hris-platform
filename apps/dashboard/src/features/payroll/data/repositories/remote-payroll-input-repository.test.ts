import { assert, describe, expect, it, vi } from "vitest";
import {
  inputCompany,
  inputEmployee,
  inputId,
  inputJob,
  inputMonth,
  inputRecord,
  inputReference,
  inputReview,
} from "../../../../../tests/fixtures/payroll-inputs";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollInputDataSource } from "../datasources/http-payroll-input-data-source";
import type { PayrollInputReviewDto } from "../models/payroll-input-dto";
import { RemotePayrollInputRepository } from "./remote-payroll-input-repository";

const company = inputCompany as CompanyId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repository: new RemotePayrollInputRepository(new HttpPayrollInputDataSource(http)),
  };
}
describe("monthly payroll input transport", () => {
  it("maps a coherent paginated review, source, exact money and immutable nested values", async () => {
    const raw = inputReview(inputRecord(23)),
      { http, repository } = fixture(raw);
    const result = await repository.review(company, inputEmployee, inputMonth, null, signal);
    assert(result.ok);
    expect(result.value.history.items).toHaveLength(20);
    expect(result.value.history.nextCursor).toBe("19");
    expect(result.value.current?.terms.earnings[0]?.amount).toBe("2000000.25");
    expect(http.request.mock.calls[0]?.[0].path).toContain(
      `/inputs/${inputMonth}/review?historyLimit=20`,
    );
    assert(raw.current);
    raw.current.terms.earnings.length = 0;
    raw.availableActions.length = 0;
    expect(result.value.current?.terms.earnings).toHaveLength(1);
    expect(result.value.availableActions).toEqual(["PREPARE"]);
    expect(Object.isFrozen(result.value.current?.terms.dayResolutions[0])).toBe(true);
    http.request.mockResolvedValue(inputReview(inputRecord(23), "19"));
    expect((await repository.review(company, inputEmployee, inputMonth, "19", signal)).ok).toBe(
      true,
    );
    expect(http.request.mock.calls.at(-1)?.[0].path).toContain("historyAfter=19");
  });
  it("accepts unprepared inputs and missing attendance without manufacturing actions", async () => {
    const raw = inputReview(null);
    raw.source = null;
    raw.availableActions = [];
    const { repository } = fixture(raw);
    expect(await repository.review(company, inputEmployee, inputMonth, null, signal)).toMatchObject(
      {
        ok: true,
        value: { source: null, current: null, history: { items: [] }, availableActions: [] },
      },
    );
  });
  it.each([
    [
      "employee scope",
      (r: PayrollInputReviewDto) => {
        r.employee.id = inputId;
      },
    ],
    [
      "month scope",
      (r: PayrollInputReviewDto) => {
        r.earningsMonth = "2026-08";
      },
    ],
    [
      "source month",
      (r: PayrollInputReviewDto) => {
        assert(r.source);
        r.source.earningsMonth = "2026-08";
      },
    ],
    [
      "history gap",
      (r: PayrollInputReviewDto) => {
        r.history.items.splice(0, 1);
      },
    ],
    [
      "mixed head",
      (r: PayrollInputReviewDto) => {
        assert(r.current);
        r.current.reason = "Changed";
      },
    ],
    [
      "cursor regression",
      (r: PayrollInputReviewDto) => {
        r.history.nextCursor = "0";
      },
    ],
    [
      "frozen action",
      (r: PayrollInputReviewDto) => {
        r.frozen = true;
      },
    ],
    [
      "source absent",
      (r: PayrollInputReviewDto) => {
        r.source = null;
      },
    ],
    [
      "changed source verification",
      (r: PayrollInputReviewDto) => {
        assert(r.source);
        r.source.version++;
        r.availableActions = ["VERIFY"];
      },
    ],
    [
      "missing verifier",
      (r: PayrollInputReviewDto) => {
        assert(r.current);
        r.current.status = "VERIFIED";
      },
    ],
    [
      "duplicate earnings",
      (r: PayrollInputReviewDto) => {
        assert(r.current);
        const line = r.current.terms.earnings[0];
        assert(line);
        r.current.terms.earnings.push({ ...line });
      },
    ],
    [
      "wrong month date",
      (r: PayrollInputReviewDto) => {
        assert(r.current);
        const day = r.current.terms.dayResolutions[0];
        assert(day);
        day.workDate = "2026-08-01";
      },
    ],
  ] as const)("rejects %s before releasing values", async (_name, change) => {
    const raw = inputReview(inputRecord(2));
    change(raw);
    const { repository } = fixture(raw);
    expect(await repository.review(company, inputEmployee, inputMonth, null, signal)).toMatchObject(
      { ok: false, failure: { code: "invalid_response" } },
    );
  });
  it("checks employee filters, bounded pages and nullable status/version pairs", async () => {
    const search = {
      month: inputMonth,
      query: "A & B",
      status: "UNPREPARED" as const,
      after: null,
    };
    const raw = {
        items: [{ ...inputReference(), inputVersion: null, inputStatus: null }],
        nextCursor: null,
      },
      { http, repository } = fixture(raw);
    expect((await repository.employees(company, search, signal)).ok).toBe(true);
    const url = new URL(http.request.mock.calls[0]?.[0].path ?? "", "https://example.invalid");
    expect(url.searchParams.get("query")).toBe("A & B");
    expect(url.searchParams.get("limit")).toBe("50");
    expect(url.searchParams.get("status")).toBe("UNPREPARED");
    http.request.mockResolvedValue({
      ...raw,
      items: [{ ...inputReference(), inputVersion: 0, inputStatus: null }],
    });
    expect((await repository.employees(company, search, signal)).ok).toBe(false);
    http.request.mockResolvedValue({ ...raw, nextCursor: "IN-001" });
    expect((await repository.employees(company, search, signal)).ok).toBe(false);
  });
  it("separates historical reads, versioned save and verification receipts", async () => {
    const { repository, http } = fixture(inputRecord(2));
    expect((await repository.revision(company, inputEmployee, inputMonth, 2, signal)).ok).toBe(
      true,
    );
    expect((await repository.revision(company, inputEmployee, inputMonth, 1, signal)).ok).toBe(
      false,
    );
    const operation = "ba900000-0000-4000-8000-000000000001" as OperationId,
      input = {
        workJobId: inputJob,
        expectedWorkPeriodVersion: 2,
        expectedEmploymentVersion: 7,
        expectedVersion: null,
        terms: inputRecord().terms,
        reason: "Prepare reviewed input",
      };
    http.request.mockResolvedValue({ id: inputId, version: 0 });
    expect(
      (await repository.save(company, inputEmployee, inputMonth, operation, input, signal)).ok,
    ).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0]).toMatchObject({
      method: "PUT",
      operationId: operation,
      body: input,
    });
    http.request.mockResolvedValue({ id: inputId, version: 1 });
    expect(
      (
        await repository.verify(
          company,
          inputEmployee,
          inputMonth,
          operation,
          { expectedVersion: 0, reason: "Independent review" },
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0].method).toBe("POST");
    http.request.mockResolvedValue({ id: inputId, version: 9 });
    expect(
      (
        await repository.verify(
          company,
          inputEmployee,
          inputMonth,
          operation,
          { expectedVersion: 0, reason: "Review" },
          signal,
        )
      ).ok,
    ).toBe(false);
  });
  it("preserves known failures and propagates cancellation during late acquisition", async () => {
    const { http, repository } = fixture(null);
    http.request.mockRejectedValue(
      new HttpResponseError(409, { code: "stale_workforce_version", fields: {}, parameters: {} }),
    );
    expect(await repository.review(company, inputEmployee, inputMonth, null, signal)).toMatchObject(
      { ok: false, failure: { code: "stale_workforce_version" } },
    );
    const abort = new AbortController();
    let release!: (value: unknown) => void;
    http.request.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const pending = repository.review(company, inputEmployee, inputMonth, null, abort.signal);
    abort.abort();
    release(inputReview());
    await expect(pending).rejects.toThrow();
  });
});
