import { assert, describe, expect, it, vi } from "vitest";
import {
  payrollCompany,
  payrollPolicyPage,
  payrollPolicyRecord,
  payrollRule,
} from "../../../../../tests/fixtures/payroll-policies";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { HttpPayrollPolicyDataSource } from "../datasources/http-payroll-policy-data-source";
import type { PayrollPolicyReviewDto } from "../models/payroll-policy-dto";
import { RemotePayrollPolicyRepository } from "./remote-payroll-policy-repository";

const company = payrollCompany as CompanyId;
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repository: new RemotePayrollPolicyRepository(new HttpPayrollPolicyDataSource(http)),
    signal: new AbortController().signal,
  };
}
describe("payroll policy transport", () => {
  it("uses scoped bounded review pages and immutable independent revisions", async () => {
    const wire = payrollPolicyPage(payrollPolicyRecord()),
      { http, repository, signal } = fixture(wire);
    const result = await repository.review(company, "2026-06", null, signal);
    expect(result).toMatchObject({
      ok: true,
      value: {
        current: { companyId: company, version: 23 },
        effective: { appliedRevision: 22 },
        history: { nextCursor: "19" },
      },
    });
    expect(http.request).toHaveBeenCalledWith(
      { path: `/api/v1/companies/${company}/payroll/policy/review?asOf=2026-06&historyLimit=20` },
      signal,
    );
    if (!result.ok) throw new Error("Expected fixture review");
    assert(wire.current, "Expected current fixture policy");
    wire.current.reviewReferences[0] = "https://example.test/changed";
    expect(result.value.current?.reviewReferences[0]).not.toContain("changed");
    expect(Object.isFrozen(result.value.history.items)).toBe(true);
    http.request.mockResolvedValue(payrollPolicyPage(payrollPolicyRecord(), "2026-06", "19"));
    expect((await repository.review(company, "2026-06", "19", signal)).ok).toBe(true);
    expect(http.request.mock.calls.at(-1)?.[0].path).toContain("historyAfter=19");
  });
  it.each([
    [
      "wrong month",
      (raw: PayrollPolicyReviewDto) => {
        raw.asOf = "2026-07";
      },
    ],
    [
      "mixed head",
      (raw: PayrollPolicyReviewDto) => {
        assert(raw.history.items[0], "Expected fixture history");
        raw.history.items[0].policy.version = 24;
      },
    ],
    [
      "skipped revision",
      (raw: PayrollPolicyReviewDto) => {
        assert(raw.history.items[0], "Expected fixture history");
        raw.history.items[0].policy.appliedRevision = 1;
      },
    ],
    [
      "looped cursor",
      (raw: PayrollPolicyReviewDto) => {
        raw.history.nextCursor = "0";
      },
    ],
    [
      "missing continuation",
      (raw: PayrollPolicyReviewDto) => {
        raw.history.nextCursor = null;
      },
    ],
    [
      "wrong head contents",
      (raw: PayrollPolicyReviewDto) => {
        assert(raw.current, "Expected current fixture policy");
        raw.current.appliedRevision = 20;
      },
    ],
    [
      "inactive month",
      (raw: PayrollPolicyReviewDto) => {
        assert(raw.effective, "Expected effective fixture policy");
        raw.effective.effectiveUntil = "2026-05";
      },
    ],
    [
      "invalid money",
      (raw: PayrollPolicyReviewDto) => {
        assert(raw.current, "Expected current fixture policy");
        raw.current.pensionWageCap = "1e8";
      },
    ],
    [
      "unsafe link",
      (raw: PayrollPolicyReviewDto) => {
        assert(raw.current, "Expected current fixture policy");
        raw.current.reviewReferences = ["javascript:alert(1)"];
      },
    ],
  ])("maps %s to an invalid response", async (_, corrupt) => {
    const raw = payrollPolicyPage(payrollPolicyRecord());
    corrupt(raw);
    const { repository, signal } = fixture(raw);
    expect(await repository.review(company, "2026-06", null, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("keeps an absent effective policy distinct from an unconfigured company", async () => {
    const { repository, signal, http } = fixture(
      payrollPolicyPage(payrollPolicyRecord(), "2028-01"),
    );
    expect(await repository.review(company, "2028-01", null, signal)).toMatchObject({
      ok: true,
      value: { current: { version: 23 }, effective: null },
    });
    http.request.mockResolvedValue({
      asOf: "2026-06",
      current: null,
      effective: null,
      history: { items: [], nextCursor: null },
    });
    expect(await repository.review(company, "2026-06", null, signal)).toMatchObject({
      ok: true,
      value: { current: null, effective: null },
    });
  });
  it("checks rule identities and dates and owns the returned source list", async () => {
    const rule = { ...payrollRule, sources: [...payrollRule.sources] },
      { repository, signal, http } = fixture([rule]);
    const result = await repository.rules(company, signal);
    expect(result.ok).toBe(true);
    rule.sources[0] = "https://example.test/changed";
    if (result.ok) expect(result.value[0]?.sources[0]).toBe(payrollRule.sources[0]);
    for (const raw of [
      [payrollRule, payrollRule],
      [{ ...payrollRule, effectiveFrom: "2024-02-30" }],
    ]) {
      http.request.mockResolvedValue(raw);
      expect(await repository.rules(company, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("binds saved receipts to the company and next version without leaking domain fields", async () => {
    const { repository, signal, http } = fixture({ id: company, version: 24 });
    const input = {
      ...payrollPolicyRecord().current,
      expectedVersion: 23,
      reason: "Reviewed company policy",
    };
    const operation = "c1000000-0000-4000-8000-000000000001" as OperationId;
    expect((await repository.save(company, operation, input, signal)).ok).toBe(true);
    const command = http.request.mock.calls[0]?.[0];
    assert(command, "Expected save request");
    expect(command).toMatchObject({
      path: `/api/v1/companies/${company}/payroll/policy`,
      method: "PUT",
      operationId: operation,
      body: { minimumMonthlyWage: "4100000.25", expectedVersion: 23 },
    });
    expect(command.body).not.toHaveProperty("version");
    expect(command.body).not.toHaveProperty("appliedRevision");
    for (const receipt of [
      { id: company, version: 23 },
      { id: "10000000-0000-4000-8000-000000000002", version: 24 },
    ]) {
      http.request.mockResolvedValue(receipt);
      expect(await repository.save(company, operation, input, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
});
