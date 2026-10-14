import { assert, describe, expect, it, vi } from "vitest";
import {
  openingCompany,
  openingEmployee,
  openingId,
  openingRecord,
  openingReference,
  openingReview,
  openingYear,
} from "../../../../../tests/fixtures/tax-openings";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpTaxOpeningDataSource } from "../datasources/http-tax-opening-data-source";
import type { TaxOpeningReviewDto } from "../models/tax-opening-dto";
import { RemoteTaxOpeningRepository } from "./remote-tax-opening-repository";

const company = openingCompany as CompanyId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return { http, repository: new RemoteTaxOpeningRepository(new HttpTaxOpeningDataSource(http)) };
}
describe("opening tax history transport", () => {
  it("maps coherent history pages and protects exact nested values from later DTO mutation", async () => {
    const raw = openingReview(openingRecord(23)),
      { http, repository } = fixture(raw);
    const result = await repository.review(company, openingEmployee, openingYear, null, signal);
    assert(result.ok);
    expect(result.value.history.items).toHaveLength(20);
    expect(result.value.history.nextCursor).toBe("19");
    expect(result.value.current?.terms.history.taxableGross).toBe("60000000.25");
    expect(http.request.mock.calls[0]?.[0].path).toContain(
      "/tax-openings/2026/review?historyLimit=20",
    );
    assert(raw.current);
    raw.current.terms.history.taxableGross = "0";
    raw.availableActions.length = 0;
    expect(result.value.current?.terms.history.taxableGross).toBe("60000000.25");
    expect(result.value.availableActions).toEqual(["PREPARE"]);
    expect(Object.isFrozen(result.value.current?.terms.history)).toBe(true);
    http.request.mockResolvedValue(openingReview(openingRecord(23), "19"));
    expect((await repository.review(company, openingEmployee, openingYear, "19", signal)).ok).toBe(
      true,
    );
    expect(http.request.mock.calls.at(-1)?.[0].path).toContain("historyAfter=19");
  });
  it("retains missing history as null instead of manufacturing zero totals", async () => {
    const { repository } = fixture(openingReview(null));
    expect(
      await repository.review(company, openingEmployee, openingYear, null, signal),
    ).toMatchObject({
      ok: true,
      value: { current: null, history: { items: [] }, availableActions: ["PREPARE"] },
    });
  });
  it.each([
    [
      "employee scope",
      (r: TaxOpeningReviewDto) => {
        r.employee.id = openingId;
      },
    ],
    [
      "year scope",
      (r: TaxOpeningReviewDto) => {
        r.year = 2025;
      },
    ],
    [
      "history gap",
      (r: TaxOpeningReviewDto) => {
        r.history.items.splice(0, 1);
      },
    ],
    [
      "mixed head",
      (r: TaxOpeningReviewDto) => {
        assert(r.current);
        r.current.reason = "Changed";
      },
    ],
    [
      "cursor regression",
      (r: TaxOpeningReviewDto) => {
        r.history.nextCursor = "0";
      },
    ],
    [
      "frozen action",
      (r: TaxOpeningReviewDto) => {
        r.frozen = true;
      },
    ],
    [
      "missing verifier",
      (r: TaxOpeningReviewDto) => {
        assert(r.current);
        r.current.status = "VERIFIED";
      },
    ],
    [
      "inconsistent employment months",
      (r: TaxOpeningReviewDto) => {
        assert(r.current);
        r.current.terms.history.employmentMonths = 0;
      },
    ],
    [
      "nonresident prior employer",
      (r: TaxOpeningReviewDto) => {
        assert(r.current);
        r.current.terms.residency = "NON_RESIDENT";
        r.current.terms.history.previousEmployerNet = "1";
      },
    ],
    [
      "annual amount ceiling",
      (r: TaxOpeningReviewDto) => {
        assert(r.current);
        r.current.terms.history.withheld = "600000000000.01";
      },
    ],
  ] as const)("rejects %s before releasing the snapshot", async (_name, change) => {
    const raw = openingReview(openingRecord(2));
    change(raw);
    const { repository } = fixture(raw);
    expect(
      await repository.review(company, openingEmployee, openingYear, null, signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("checks nullable lookup heads and keeps its filters and page size", async () => {
    const search = {
      year: openingYear,
      query: "A & B",
      status: "UNPREPARED" as const,
      after: null,
    };
    const raw = {
        items: [{ ...openingReference(), openingVersion: null, openingStatus: null }],
        nextCursor: null,
      },
      { http, repository } = fixture(raw);
    expect((await repository.employees(company, search, signal)).ok).toBe(true);
    const url = new URL(http.request.mock.calls[0]?.[0].path ?? "", "https://example.invalid");
    expect(url.searchParams.get("query")).toBe("A & B");
    expect(url.searchParams.get("year")).toBe("2026");
    expect(url.searchParams.get("limit")).toBe("50");
    http.request.mockResolvedValue({
      ...raw,
      items: [{ ...openingReference(), openingVersion: 0, openingStatus: null }],
    });
    expect((await repository.employees(company, search, signal)).ok).toBe(false);
    http.request.mockResolvedValue({ ...raw, nextCursor: "TX-001" });
    expect((await repository.employees(company, search, signal)).ok).toBe(false);
  });
  it("separates exact revision reads from versioned mutation receipts", async () => {
    const { repository, http } = fixture(openingRecord(2));
    expect((await repository.revision(company, openingEmployee, openingYear, 2, signal)).ok).toBe(
      true,
    );
    expect((await repository.revision(company, openingEmployee, openingYear, 1, signal)).ok).toBe(
      false,
    );
    const operation = "bc900000-0000-4000-8000-000000000001" as OperationId,
      input = {
        expectedEmploymentVersion: 7,
        expectedVersion: null,
        terms: openingRecord().terms,
        reason: "Prepare reviewed history",
      };
    http.request.mockResolvedValue({ id: openingId, version: 0 });
    expect(
      (await repository.save(company, openingEmployee, openingYear, operation, input, signal)).ok,
    ).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0]).toMatchObject({
      method: "PUT",
      operationId: operation,
      body: input,
    });
    http.request.mockResolvedValue({ id: openingId, version: 1 });
    expect(
      (
        await repository.verify(
          company,
          openingEmployee,
          openingYear,
          operation,
          { expectedVersion: 0, reason: "Review" },
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0].method).toBe("POST");
    http.request.mockResolvedValue({ id: openingId, version: 9 });
    expect(
      (
        await repository.verify(
          company,
          openingEmployee,
          openingYear,
          operation,
          { expectedVersion: 0, reason: "Review" },
          signal,
        )
      ).ok,
    ).toBe(false);
  });
  it("preserves classified failures and aborts late acquisition", async () => {
    const { http, repository } = fixture(null);
    http.request.mockRejectedValue(
      new HttpResponseError(409, { code: "stale_employment_version", fields: {}, parameters: {} }),
    );
    expect(
      await repository.review(company, openingEmployee, openingYear, null, signal),
    ).toMatchObject({ ok: false, failure: { code: "stale_employment_version" } });
    const abort = new AbortController();
    let release!: (value: unknown) => void;
    http.request.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const pending = repository.review(company, openingEmployee, openingYear, null, abort.signal);
    abort.abort();
    release(openingReview());
    await expect(pending).rejects.toThrow();
  });
});
