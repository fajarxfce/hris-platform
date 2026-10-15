import { assert, describe, expect, it, vi } from "vitest";
import {
  amendmentPreparation,
  amendmentPreview,
} from "../../../../../tests/fixtures/payroll-amendment-authoring";
import { amendmentId, amendmentRevision } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId } from "../../../../../tests/fixtures/payroll-payslips";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentSourceSearch } from "../../domain/entities/payroll-amendment-preparation";
import { HttpPayrollAmendmentDataSource } from "../datasources/http-payroll-amendment-data-source";
import { HttpPayrollAmendmentSourceDataSource } from "../datasources/http-payroll-amendment-source-data-source";
import type { PayrollAmendmentPreparationDto } from "../models/payroll-amendment-preparation-dto";
import { RemotePayrollAmendmentRepository } from "./remote-payroll-amendment-repository";
import { RemotePayrollAmendmentSourceRepository } from "./remote-payroll-amendment-source-repository";

const company = calculationCompany as CompanyId,
  signal = new AbortController().signal,
  operation = amendmentId(40) as OperationId;
const search: PayrollAmendmentSourceSearch = {
  year: 2026,
  query: "",
  employeeId: null,
  after: null,
};
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return {
    http,
    sources: new RemotePayrollAmendmentSourceRepository(
      new HttpPayrollAmendmentSourceDataSource(http),
    ),
    drafts: new RemotePayrollAmendmentRepository(new HttpPayrollAmendmentDataSource(http)),
  };
}
function candidates(count = 1) {
  return {
    items: Array.from({ length: count }, (_, i) => ({
      assessmentId: payslipId(i + 1),
      employeeId: amendmentId(i + 1),
      employeeNumber: `EMP-${i}`,
      employeeName: `Employee ${i}`,
      taxMonth: "2026-09",
      revision: 0,
      publishedAt: "2026-10-01T01:00:00Z",
    })),
    nextCursor: null as string | null,
  };
}
describe("amendment source and save transport", () => {
  it("acquires scoped minimal sources with literal filters and bounded pages", async () => {
    const raw = candidates(),
      { http, sources } = fixture(raw),
      result = await sources.list(company, { ...search, query: "A%_ B" }, signal);
    assert(result.ok);
    expect(result.value.items[0]?.companyId).toBe(company);
    const path = new URL(http.request.mock.calls[0]?.[0].path ?? "", "http://test.invalid");
    expect(path.searchParams.get("query")).toBe("A%_ B");
    expect(path.searchParams.get("limit")).toBe("50");
    assert(raw.items[0]);
    raw.items[0].employeeName = "Different";
    expect(result.value.items[0]?.employeeName).toBe("Employee 0");
    expect(Object.isFrozen(result.value.items[0])).toBe(true);
  });
  it.each(["year", "employee", "order", "cursor", "bound"])(
    "rejects a directory outside %s",
    async (kind) => {
      const raw = candidates(),
        query = { ...search };
      if (kind === "year") query.year = 2025;
      if (kind === "employee") query.employeeId = amendmentId(2);
      if (kind === "order") raw.items.push(...raw.items);
      if (kind === "cursor") raw.nextCursor = payslipId(2);
      if (kind === "bound") raw.items = candidates(51).items;
      expect(await fixture(raw).sources.list(company, query, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );
  it("retains complete year evidence without sharing nested DTO values", async () => {
    const raw = amendmentPreparation(true),
      { http, sources } = fixture(raw),
      result = await sources.prepare(company, payslipId(), signal);
    assert(result.ok);
    assert(raw.assessments[0]);
    assert(raw.sourceVersions[0]);
    raw.assessments[0].compensation.basicSalary = "1";
    raw.sourceVersions[0].revision = 99;
    expect(result.value.assessments).toHaveLength(3);
    expect(result.value.assessments[0]?.compensation.basicSalary).not.toBe("1");
    expect(result.value.sourceVersions[0]?.revision).toBe(0);
    expect(http.request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${company}/payroll/payslips/${payslipId()}/amendment-preparation`,
    );
  });
  it.each<[string, (raw: PayrollAmendmentPreparationDto) => void]>([
    [
      "substituted first assessment",
      (r) => {
        r.firstAssessmentId = payslipId(99);
      },
    ],
    [
      "duplicate source",
      (r) => {
        assert(r.sourceVersions[0]);
        r.sourceVersions.push({ ...r.sourceVersions[0] });
      },
    ],
    [
      "mixed revision",
      (r) => {
        assert(r.assessments[0]);
        r.assessments[0].source.revision = 1;
      },
    ],
    [
      "unordered months",
      (r) => {
        r.assessments.reverse();
        r.sourceVersions.reverse();
      },
    ],
    [
      "another year",
      (r) => {
        assert(r.assessments[2]);
        r.assessments[2].month = "2027-10";
      },
    ],
  ])("rejects %s within the safe boundary", async (_, corrupt) => {
    const raw = amendmentPreparation(true);
    corrupt(raw);
    expect(await fixture(raw).sources.prepare(company, payslipId(), signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("posts preview terms without a mutation operation and checks its retained basis", async () => {
    const terms = amendmentRevision(0).terms,
      { http, sources } = fixture(amendmentPreview());
    expect((await sources.preview(company, payslipId(), terms, signal)).ok).toBe(true);
    expect(http.request.mock.calls[0]?.[0]).toMatchObject({ method: "POST", body: terms });
    expect(http.request.mock.calls[0]?.[0].operationId).toBeUndefined();
    const substituted = amendmentPreview();
    assert(substituted.sourceVersions[0]);
    assert(substituted.calculations[0]);
    substituted.sourceVersions[0].revision = 1;
    substituted.calculations[0].source.revision = 1;
    http.request.mockResolvedValue(substituted);
    expect(await sources.preview(company, payslipId(), terms, signal)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it.each([null, 7])(
    "sends one exact save with observed version %s and validates the receipt",
    async (expectedVersion) => {
      const input = {
          ...amendmentRevision(0).terms,
          firstAssessmentId: payslipId(),
          expectedVersion,
          reason: "Verified correction",
        },
        { drafts, http } = fixture({ id: amendmentId(), version: (expectedVersion ?? -1) + 1 });
      const result = await drafts.save(company, amendmentId(), operation, input, signal);
      assert(result.ok);
      expect(Object.isFrozen(result.value)).toBe(true);
      const request = http.request.mock.calls[0]?.[0];
      expect(request).toMatchObject({
        method: "PUT",
        operationId: operation,
        body: input,
        path: `/api/v1/companies/${company}/payroll/amendments/${amendmentId()}`,
      });
      http.request.mockResolvedValue({ id: amendmentId(), version: 999 });
      expect(await drafts.save(company, amendmentId(), operation, input, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
      http.request.mockResolvedValue({ id: amendmentId(2), version: (expectedVersion ?? -1) + 1 });
      expect(await drafts.save(company, amendmentId(), operation, input, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    },
  );
  it("preserves safe denial and cancellation without a retry", async () => {
    const { http, sources } = fixture(null);
    http.request.mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "access_denied",
        detail: "PRIVATE token",
        fields: {},
        parameters: {},
      }),
    );
    const result = await sources.prepare(company, payslipId(), signal);
    expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(http.request).toHaveBeenCalledTimes(1);
    const abort = new AbortController();
    http.request.mockImplementationOnce(async () => {
      abort.abort();
      return amendmentPreparation();
    });
    await expect(sources.prepare(company, payslipId(), abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
