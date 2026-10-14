import { assert, describe, expect, it, vi } from "vitest";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import {
  payslipDetails,
  payslipId,
  payslipPayments,
  payslipPdf,
  payslipSummary,
} from "../../../../../tests/fixtures/payroll-payslips";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { HttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPayslipSearch } from "../../domain/entities/payroll-payslip";
import { HttpPayrollPayslipDataSource } from "../datasources/http-payroll-payslip-data-source";
import type { PayrollPayslipDto } from "../models/payroll-payslip-dto";
import { RemotePayrollPayslipRepository } from "./remote-payroll-payslip-repository";

const company = calculationCompany as CompanyId;
const signal = () => new AbortController().signal;
const search: PayrollPayslipSearch = {
  from: "2026-01",
  until: "2026-12",
  query: "Example & + %",
  employeeId: null,
  scope: "SELF",
  after: null,
};
function fixture(raw: unknown) {
  const http = { request: vi.fn<HttpClient["request"]>().mockResolvedValue(raw) };
  return { http, repo: new RemotePayrollPayslipRepository(new HttpPayrollPayslipDataSource(http)) };
}
describe("payslip mapping and acquisition", () => {
  it("keeps exact signed amounts, immutable detail and retained labels without source profiles", async () => {
    const raw = payslipDetails(),
      { repo, http } = fixture(raw);
    const result = await repo.get(company, payslipId(), "SELF", signal());
    assert(result.ok);
    expect(result.value.summary.withheld).toBe("-0.50");
    expect(Object.isFrozen(result.value.calculation.earnings[0])).toBe(true);
    raw.summary.employeeName = "New profile name";
    raw.calculation.tax.withheld = "0";
    expect(result.value.summary.employeeName).toBe("Example employee 1");
    expect(result.value.calculation.tax.withheld).toBe("-0.50");
    expect(http.request).toHaveBeenCalledOnce();
    expect(http.request.mock.calls[0]?.[0].path).toContain("scope=SELF");
    expect(result.value).not.toHaveProperty("facts");
  });
  it.each<[string, (raw: PayrollPayslipDto) => void]>([
    [
      "wrong reference",
      (r) => {
        r.summary.id = payslipId(2);
      },
    ],
    [
      "contradictory totals",
      (r) => {
        r.calculation.tax.takeHome = "10000000.74";
      },
    ],
    [
      "float money",
      (r) => {
        Object.assign(r.summary, { takeHome: 10000000.75 });
      },
    ],
    [
      "unknown currency",
      (r) => {
        Object.assign(r.summary, { currency: "USD" });
      },
    ],
  ])("rejects %s without leaking technical details", async (_, corrupt) => {
    const raw = payslipDetails();
    corrupt(raw);
    expect(await fixture(raw).repo.get(company, payslipId(), "SELF", signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("encodes submitted filters and accepts ordered cursor pages", async () => {
    const items = Array.from({ length: 50 }, (_, i) => payslipSummary(i + 1)),
      cursor = `2026-09:${payslipId(50)}`;
    const { repo, http } = fixture({ items, nextCursor: cursor });
    const result = await repo.list(company, search, signal());
    assert(result.ok);
    expect(result.value.nextCursor).toBe(cursor);
    expect(Object.isFrozen(result.value.items)).toBe(true);
    const url = new URL(http.request.mock.calls[0]?.[0].path ?? "", "http://example.invalid");
    expect(url.searchParams.get("query")).toBe(search.query);
    expect(url.searchParams.get("limit")).toBe("50");
    expect(url.searchParams.get("scope")).toBe("SELF");
    http.request.mockResolvedValue({ items: [payslipSummary(51)], nextCursor: null });
    expect((await repo.list(company, { ...search, after: cursor }, signal())).ok).toBe(true);
  });
  it.each([
    { items: [payslipSummary(1), payslipSummary(1)], nextCursor: null },
    { items: [payslipSummary(2), payslipSummary(1)], nextCursor: null },
    { items: [payslipSummary(1, { taxMonth: "2025-12" })], nextCursor: null },
    { items: [payslipSummary(1)], nextCursor: `2026-09:${payslipId(1)}` },
  ])("rejects duplicate, unordered, out-of-filter and non-progressing pages", async (raw) => {
    expect(await fixture(raw).repo.list(company, search, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("maps independently versioned payment attempts without mistaking planned dates for settlement", async () => {
    const raw = payslipPayments(),
      { repo } = fixture(raw);
    const result = await repo.payments(company, payslipId(), "AUTHORIZED", signal());
    assert(result.ok);
    expect(result.value.version).toBe(3);
    expect(result.value.attempts.map((x) => x.status)).toEqual(["PENDING", "FAILED"]);
    assert(raw.attempts[0]);
    raw.attempts[0].status = "SUCCEEDED";
    expect(result.value.attempts[0]?.status).toBe("PENDING");
    raw.assessmentId = payslipId(2);
    expect(await repo.payments(company, payslipId(), "SELF", signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("requests one bounded localized PDF and checks its digest before transferring owned buffers", async () => {
    const raw = payslipPdf(),
      { repo, http } = fixture(raw);
    const result = await repo.pdf(company, payslipId(), "SELF", "EN", signal());
    assert(result.ok);
    expect(result.value.byteLength).toBe(raw.byteLength);
    assert(result.value.parts[0]);
    expect(new Uint8Array(result.value.parts[0])[0]).toBe(37);
    expect(http.request).toHaveBeenCalledOnce();
    expect(http.request.mock.calls[0]?.[0]).toMatchObject({
      response: { maximumBytes: 1024 * 1024 },
    });
    expect(http.request.mock.calls[0]?.[0].path).toContain("scope=SELF&language=EN");
  });
  it.each(["digest", "abort"])("clears acquired bytes after %s failure", async (failure) => {
    const raw = payslipPdf(),
      owner = new AbortController(),
      { repo, http } = fixture(raw);
    if (failure === "digest") raw.etag = `"${"0".repeat(64)}"`;
    else
      http.request.mockImplementation(async () => {
        owner.abort();
        return raw;
      });
    const result = repo.pdf(company, payslipId(), "SELF", "ID", owner.signal);
    if (failure === "abort") await expect(result).rejects.toMatchObject({ name: "AbortError" });
    else expect(await result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(new Uint8Array(raw.parts[0]).every((value) => value === 0)).toBe(true);
  });
  it("retains server failure classification and never retries PDF capacity automatically", async () => {
    const { repo, http } = fixture(null);
    http.request.mockRejectedValue(
      new HttpResponseError(429, {
        code: "payroll_pdf_capacity",
        retryAfterSeconds: 2,
        detail: "PRIVATE",
      }),
    );
    const result = await repo.pdf(company, payslipId(), "SELF", "ID", signal());
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "payroll_pdf_capacity", retryAfterSeconds: 2 },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(http.request).toHaveBeenCalledOnce();
  });
});
