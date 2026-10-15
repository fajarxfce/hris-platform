import { assert, describe, expect, it, vi } from "vitest";
import {
  amendmentCalculation,
  amendmentCalculationId,
  amendmentDetails,
  amendmentId,
  amendmentRevision,
  amendmentSummary,
} from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentSearch } from "../../domain/entities/payroll-amendment-search";
import { HttpPayrollAmendmentDataSource } from "../datasources/http-payroll-amendment-data-source";
import type {
  PayrollAmendmentCalculationDto,
  PayrollAmendmentDetailsDto,
} from "../models/payroll-amendment-dto";
import { RemotePayrollAmendmentRepository } from "./remote-payroll-amendment-repository";

const company = calculationCompany as CompanyId,
  signal = new AbortController().signal,
  search: PayrollAmendmentSearch = {
    year: 2026,
    employeeId: null,
    query: "",
    status: null,
    after: null,
  };
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    repo: new RemotePayrollAmendmentRepository(new HttpPayrollAmendmentDataSource(http)),
  };
}
describe("amendment read transport", () => {
  it("preserves literal filters and immutable scoped references", async () => {
    const raw = { items: [amendmentSummary()], nextCursor: null },
      { repo, http } = fixture(raw),
      result = await repo.list(company, { ...search, query: "A%_ B" }, signal);
    assert(result.ok);
    expect(result.value.items[0]?.companyId).toBe(company);
    const url = new URL(http.request.mock.calls[0]?.[0].path ?? "", "http://test.invalid");
    expect(url.searchParams.get("query")).toBe("A%_ B");
    expect(url.searchParams.get("limit")).toBe("50");
    assert(raw.items[0]);
    raw.items[0].employee.name = "Changed";
    expect(result.value.items[0]?.employee.name).toBe("Amendment employee 1");
    expect(Object.isFrozen(result.value.items)).toBe(true);
  });
  it("acquires exact retained revisions and bounded history", async () => {
    const { repo, http } = fixture(amendmentRevision(0));
    expect((await repo.revision(company, amendmentId(), 0, signal)).ok).toBe(true);
    expect(http.request.mock.calls[0]?.[0].path).toContain("/revisions/0");
    http.request.mockResolvedValue({ items: [amendmentSummary(25)], nextCursor: null });
    expect((await repo.history(company, amendmentId(), 24, signal)).ok).toBe(true);
    expect(http.request.mock.calls[1]?.[0].path).toContain("history?after=24&limit=25");
    http.request.mockResolvedValue(amendmentRevision(1));
    expect(await repo.revision(company, amendmentId(), 0, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("maps complete details without sharing nested proposal or history values", async () => {
    const raw = amendmentDetails(),
      { repo } = fixture(raw),
      result = await repo.get(company, amendmentId(), signal);
    assert(result.ok);
    const term = result.value.amendment.terms.corrections[0]?.compensation;
    expect(term?.basicSalary).toBe("9999999.25");
    expect(Object.isFrozen(term?.fixedEarnings[0])).toBe(true);
    assert(raw.amendment.terms.corrections[0]?.compensation);
    raw.amendment.terms.corrections[0].compensation.basicSalary = "1";
    assert(raw.amendment.terms.sourceVersions[0]);
    raw.amendment.terms.sourceVersions[0].revision = 99;
    expect(term?.basicSalary).toBe("9999999.25");
    expect(result.value.amendment.terms.sourceVersions[0]?.revision).toBe(0);
  });
  it.each<[string, (raw: PayrollAmendmentDetailsDto) => void]>([
    [
      "wrong amendment",
      (r) => {
        r.amendment.summary.id = amendmentId(2);
      },
    ],
    [
      "missing source",
      (r) => {
        assert(r.amendment.terms.sourceVersions[0]);
        r.amendment.terms.sourceVersions[0].assessmentId = amendmentId(2);
      },
    ],
    [
      "duplicate source",
      (r) => {
        assert(r.amendment.terms.sourceVersions[0]);
        r.amendment.terms.sourceVersions.push({ ...r.amendment.terms.sourceVersions[0] });
      },
    ],
    [
      "empty correction",
      (r) => {
        assert(r.amendment.terms.corrections[0]);
        r.amendment.terms.corrections[0].compensation = null;
        r.amendment.terms.corrections[0].input = null;
      },
    ],
    [
      "invalid tax date",
      (r) => {
        assert(r.amendment.terms.corrections[0]?.compensation);
        r.amendment.terms.corrections[0].compensation.tax.verifiedOn = "2026-02-31";
      },
    ],
    [
      "wrong calculation",
      (r) => {
        assert(r.latestCalculation);
        r.latestCalculation.amendmentId = amendmentId(2);
      },
    ],
    [
      "substituted calculation",
      (r) => {
        assert(r.latestCalculation);
        r.latestCalculation.id = amendmentId(2);
      },
    ],
    [
      "future calculation",
      (r) => {
        assert(r.latestCalculation);
        r.latestCalculation.draftVersion = r.amendment.summary.version;
      },
    ],
    [
      "mixed current history",
      (r) => {
        assert(r.history.items[1]);
        r.history.items[1].reason = "Different revision";
      },
    ],
    [
      "out of order history",
      (r) => {
        r.history.items.reverse();
      },
    ],
    [
      "missing next page",
      (r) => {
        r.history.nextCursor = "1";
      },
    ],
    [
      "contradictory status",
      (r) => {
        r.amendment.summary.status = "PUBLISHED";
      },
    ],
    [
      "duplicate action",
      (r) => {
        r.availableActions.push("EDIT");
      },
    ],
  ])("rejects %s inside the safe boundary", async (_, corrupt) => {
    const raw = amendmentDetails();
    corrupt(raw);
    expect(await fixture(raw).repo.get(company, amendmentId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it.each(["year", "employee", "status", "order", "cursor", "size"])(
    "rejects a directory outside %s",
    async (kind) => {
      const raw = { items: [amendmentSummary()], nextCursor: null as string | null };
      const query = { ...search };
      if (kind === "year") query.year = 2025;
      if (kind === "employee") query.employeeId = amendmentId(2);
      if (kind === "status") query.status = "CANCELLED";
      if (kind === "order") raw.items.push(amendmentSummary());
      if (kind === "cursor") raw.nextCursor = amendmentId(2);
      if (kind === "size")
        raw.items = Array.from({ length: 51 }, (_, i) => amendmentSummary(1, i + 1));
      expect(await fixture(raw).repo.list(company, query, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );
  it("retains signed differences exactly and distinguishes a failed rule attempt", async () => {
    const raw = amendmentCalculation(),
      { repo, http } = fixture(raw),
      result = await repo.calculation(company, amendmentId(), amendmentCalculationId, signal);
    assert(result.ok);
    expect(result.value.preview?.difference.takeHome).toBe("-1.00");
    expect(result.value.preview?.calculations[0]?.original.tax.withheld).toBe("-0.50");
    expect(Object.isFrozen(result.value.preview?.calculations[0]?.calculation.tax)).toBe(true);
    http.request.mockResolvedValue(amendmentCalculation(1, true));
    const failed = await repo.calculation(company, amendmentId(), amendmentCalculationId, signal);
    assert(failed.ok);
    expect(failed.value.preview).toBeNull();
    expect(failed.value.summary.failure?.code).toBe("payroll_compensation_required");
  });
  it.each<[string, (raw: PayrollAmendmentCalculationDto) => void]>([
    [
      "wrong attempt",
      (r) => {
        r.summary.id = amendmentId(2);
      },
    ],
    [
      "wrong assessment count",
      (r) => {
        r.summary.assessmentCount = 2;
      },
    ],
    [
      "mismatched source revision",
      (r) => {
        assert(r.preview?.sourceVersions[0]);
        r.preview.sourceVersions[0].revision = 1;
      },
    ],
    [
      "substituted first assessment",
      (r) => {
        assert(r.preview);
        r.preview.firstAssessmentId = amendmentId(2);
      },
    ],
    [
      "incorrect signed difference",
      (r) => {
        assert(r.preview?.calculations[0]);
        r.preview.calculations[0].difference.takeHome = "1.00";
      },
    ],
    [
      "incorrect aggregate",
      (r) => {
        assert(r.preview);
        r.preview.difference.taxableGross = "0.00";
      },
    ],
    [
      "failed attempt with preview",
      (r) => {
        r.summary = amendmentCalculation(1, true).summary;
      },
    ],
    [
      "duplicate month",
      (r) => {
        assert(r.preview?.calculations[0]);
        r.preview.calculations.push({ ...r.preview.calculations[0] });
      },
    ],
  ])("rejects %s without exposing invalid calculations", async (_, corrupt) => {
    const raw = amendmentCalculation();
    corrupt(raw);
    expect(
      await fixture(raw).repo.calculation(company, amendmentId(), amendmentCalculationId, signal),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves safe failures and cancellation without hidden retries", async () => {
    const { repo, http } = fixture(null);
    http.request.mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "access_denied",
        fields: {},
        parameters: {},
        detail: "PRIVATE sentinel",
      }),
    );
    const failure = await repo.get(company, amendmentId(), signal);
    expect(failure).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(JSON.stringify(failure)).not.toContain("PRIVATE");
    expect(http.request).toHaveBeenCalledTimes(1);
    const abort = new AbortController();
    http.request.mockImplementationOnce(async () => {
      abort.abort();
      return amendmentDetails();
    });
    await expect(repo.get(company, amendmentId(), abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
