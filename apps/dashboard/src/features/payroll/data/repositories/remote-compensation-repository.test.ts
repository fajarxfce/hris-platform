import { assert, describe, expect, it, vi } from "vitest";
import {
  compensationCompany,
  compensationEmployee,
  compensationPage,
  compensationRecord,
  compensationReference,
  compensationTerms,
} from "../../../../../tests/fixtures/compensations";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpCompensationDataSource } from "../datasources/http-compensation-data-source";
import type { CompensationReviewDto } from "../models/compensation-dto";
import { RemoteCompensationRepository } from "./remote-compensation-repository";

const company = compensationCompany as CompanyId,
  signal = new AbortController().signal;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repository: new RemoteCompensationRepository(new HttpCompensationDataSource(http)),
  };
}
describe("compensation transport and coherent snapshots", () => {
  it("acquires bounded review/history with independent employment and salary heads", async () => {
    const raw = compensationPage(compensationRecord()),
      { http, repository } = fixture(raw);
    const result = await repository.review(company, compensationEmployee, "2026-06", null, signal);
    expect(result).toMatchObject({
      ok: true,
      value: {
        employee: { employmentVersion: 7 },
        current: { version: 23 },
        effective: { appliedRevision: 22 },
        history: { nextCursor: "19" },
      },
    });
    expect(http.request).toHaveBeenCalledWith(
      {
        path: `/api/v1/companies/${company}/payroll/employees/${compensationEmployee}/compensation/review?asOf=2026-06&historyLimit=20`,
      },
      signal,
    );
    assert(result.ok);
    assert(raw.current);
    raw.current.terms.tax.verificationReference = "Changed";
    raw.current.terms.fixedEarnings.length = 0;
    expect(result.value.current?.terms.tax.verificationReference).not.toBe("Changed");
    expect(result.value.current?.terms.fixedEarnings).toHaveLength(1);
    expect(Object.isFrozen(result.value.current?.terms.payBasis)).toBe(true);
    http.request.mockResolvedValue(compensationPage(compensationRecord(), "2026-06", "19"));
    expect(
      (await repository.review(company, compensationEmployee, "2026-06", "19", signal)).ok,
    ).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0].path).toContain("historyAfter=19");
  });
  it("accepts empty configuration, a month before commencement and set-order variation", async () => {
    const record = compensationRecord(0),
      { http, repository } = fixture(compensationPage(record, "2025-01"));
    expect(
      await repository.review(company, compensationEmployee, "2025-01", null, signal),
    ).toMatchObject({
      ok: true,
      value: { current: { version: 0 }, effective: null, employee: { employmentVersion: 7 } },
    });
    const page = compensationPage(record, "2027-01");
    assert(page.effective);
    page.effective.terms.insurancePrograms.reverse();
    http.request.mockResolvedValue(page);
    expect(
      (await repository.review(company, compensationEmployee, "2027-01", null, signal)).ok,
    ).toBe(true);
    http.request.mockResolvedValue({
      ...page,
      current: null,
      effective: null,
      history: { items: [], nextCursor: null },
    });
    expect(
      await repository.review(company, compensationEmployee, "2027-01", null, signal),
    ).toMatchObject({ ok: true, value: { current: null, effective: null } });
  });
  it.each([
    [
      "another employee",
      (raw: CompensationReviewDto) => {
        raw.employee.id = "ca000000-0000-4000-8000-000000000002";
      },
    ],
    [
      "another month",
      (raw: CompensationReviewDto) => {
        raw.asOf = "2026-07";
      },
    ],
    [
      "mixed salary head",
      (raw: CompensationReviewDto) => {
        assert(raw.effective);
        raw.effective.version = 22;
      },
    ],
    [
      "skipped history",
      (raw: CompensationReviewDto) => {
        raw.history.items.shift();
      },
    ],
    [
      "missing continuation",
      (raw: CompensationReviewDto) => {
        raw.history.nextCursor = null;
      },
    ],
    [
      "looped continuation",
      (raw: CompensationReviewDto) => {
        raw.history.nextCursor = "0";
      },
    ],
    [
      "unreviewed insurance exclusion",
      (raw: CompensationReviewDto) => {
        assert(raw.current);
        raw.current.terms.insurancePrograms = [];
      },
    ],
    [
      "invalid verification date",
      (raw: CompensationReviewDto) => {
        assert(raw.current);
        raw.current.terms.tax.verifiedOn = "2026-02-30";
      },
    ],
    [
      "unsafe money notation",
      (raw: CompensationReviewDto) => {
        assert(raw.current);
        raw.current.terms.basicSalary = "1e7";
      },
    ],
    [
      "employment version precision",
      (raw: CompensationReviewDto) => {
        raw.employee.employmentVersion = Number.MAX_SAFE_INTEGER + 1;
      },
    ],
  ])("rejects %s inside the failure boundary", async (_, corrupt) => {
    const raw = compensationPage(compensationRecord());
    corrupt(raw);
    const { repository } = fixture(raw);
    expect(
      await repository.review(company, compensationEmployee, "2026-06", null, signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("encodes literal lookup and rejects duplicates or nonadvancing cursors", async () => {
    const { http, repository } = fixture({
      items: [{ ...compensationReference, privateNationalId: "NEVER-IN-DOMAIN" }],
      nextCursor: null,
    });
    const result = await repository.employees(company, "Rani_20%", null, signal);
    assert(result.ok);
    expect(result.value.items[0]).not.toHaveProperty("privateNationalId");
    const request = http.request.mock.calls[0]?.[0];
    assert(request);
    const url = new URL(request.path, "https://example.test");
    expect(url.searchParams.get("query")).toBe("Rani_20%");
    expect(url.searchParams.get("limit")).toBe("20");
    http.request.mockResolvedValue({
      items: [compensationReference, compensationReference],
      nextCursor: null,
    });
    expect((await repository.employees(company, "", null, signal)).ok).toBe(false);
    http.request.mockResolvedValue({
      items: [compensationReference],
      nextCursor: compensationReference.employeeNumber,
    });
    expect(
      (await repository.employees(company, "", compensationReference.employeeNumber, signal)).ok,
    ).toBe(false);
  });
  it("projects command fields, pins both versions, and verifies the receipt identity", async () => {
    const { http, repository } = fixture({ id: compensationEmployee, version: 24 }),
      operation = "ca200000-0000-4000-8000-000000000001" as OperationId;
    const input = {
      ...compensationRecord().current,
      terms: compensationTerms(),
      expectedVersion: 23,
      expectedEmploymentVersion: 7,
      reason: "Reviewed",
    };
    expect(
      (await repository.save(company, compensationEmployee, operation, input, signal)).ok,
    ).toBe(true);
    const request = http.request.mock.calls[0]?.[0];
    assert(request);
    expect(request).toMatchObject({
      method: "PUT",
      operationId: operation,
      body: {
        expectedVersion: 23,
        expectedEmploymentVersion: 7,
        terms: { basicSalary: "10000000.25" },
      },
    });
    expect(request.body).not.toHaveProperty("appliedRevision");
    expect(request.body).not.toHaveProperty("employeeName");
    for (const receipt of [
      { id: compensationEmployee, version: 23 },
      { id: company, version: 24 },
    ]) {
      http.request.mockResolvedValue(receipt);
      expect(
        (await repository.save(company, compensationEmployee, operation, input, signal)).ok,
      ).toBe(false);
    }
  });
});
