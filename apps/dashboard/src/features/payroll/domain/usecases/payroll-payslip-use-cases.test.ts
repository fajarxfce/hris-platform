import { assert, describe, expect, it, vi } from "vitest";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId, payslipPdf } from "../../../../../tests/fixtures/payroll-payslips";
import type { FileRepository } from "../../../../core/domain/files/file-repository";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { PayrollPayslipSearch } from "../entities/payroll-payslip";
import type { PayrollPayslipRepository } from "../repositories/payroll-payslip-repository";
import { DownloadPayrollPayslip } from "./download-payroll-payslip";
import { ListPayrollPayslips } from "./list-payroll-payslips";
import { LoadPayrollPayslip } from "./load-payroll-payslip";
import { LoadPayrollPayslipPayments } from "./load-payroll-payslip-payments";

const access = { companyId: calculationCompany as CompanyId, permissions: ["payroll.self.read"] };
const search: PayrollPayslipSearch = {
  from: "2026-01",
  until: "2026-12",
  query: "  Archive  ",
  employeeId: null,
  scope: "SELF",
  after: null,
};
const signal = () => new AbortController().signal;
function repository() {
  return {
    list: vi.fn<PayrollPayslipRepository["list"]>().mockResolvedValue(failed("request_failed")),
    get: vi.fn<PayrollPayslipRepository["get"]>().mockResolvedValue(failed("request_failed")),
    payments: vi
      .fn<PayrollPayslipRepository["payments"]>()
      .mockResolvedValue(failed("request_failed")),
    pdf: vi.fn<PayrollPayslipRepository["pdf"]>().mockResolvedValue(failed("request_failed")),
  };
}
describe("payslip application policy", () => {
  it.each(["company.admin", "payroll.calculate", "payroll.review", "payroll.finalize"])(
    "does not widen %s to payslip reads or downloads",
    async (permission) => {
      const repo = repository(),
        actor = { ...access, permissions: [permission] },
        files = { downloadBinary: vi.fn<FileRepository["downloadBinary"]>() };
      for (const result of await Promise.all([
        new ListPayrollPayslips(repo).execute(actor, search, signal()),
        new LoadPayrollPayslip(repo).execute(actor, payslipId(), "AUTHORIZED", signal()),
        new LoadPayrollPayslipPayments(repo).execute(actor, payslipId(), "AUTHORIZED", signal()),
        new DownloadPayrollPayslip(repo, files).execute(
          actor,
          payslipId(),
          "AUTHORIZED",
          "ID",
          signal(),
        ),
      ]))
        expect(result).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      for (const method of Object.values(repo)) expect(method).not.toHaveBeenCalled();
      expect(files.downloadBinary).not.toHaveBeenCalled();
    },
  );
  it("normalizes submitted search without changing its explicit self scope", async () => {
    const repo = repository();
    await new ListPayrollPayslips(repo).execute(
      { ...access, permissions: ["payroll.read"] },
      search,
      signal(),
    );
    expect(repo.list).toHaveBeenCalledWith(
      access.companyId,
      { ...search, query: "Archive" },
      expect.any(AbortSignal),
    );
  });
  it.each<Partial<PayrollPayslipSearch>>([
    { from: "2023-01" },
    { until: "2029-01" },
    { from: "2026-12", until: "2026-01" },
    { after: `2025-12:${payslipId()}` },
    { after: `2026-12:${payslipId()}x` },
    { employeeId: "../employee" },
    { query: "x".repeat(121) },
    { query: "a\u0000b" },
    { query: "a\u0085b" },
  ])("rejects invalid filters before network acquisition: %j", async (override) => {
    const repo = repository();
    expect(
      (await new ListPayrollPayslips(repo).execute(access, { ...search, ...override }, signal()))
        .ok,
    ).toBe(false);
    expect(repo.list).not.toHaveBeenCalled();
  });
  it("cancels before I/O and preserves a scoped repository failure", async () => {
    const repo = repository(),
      owner = new AbortController();
    owner.abort();
    await expect(
      new LoadPayrollPayslip(repo).execute(access, payslipId(), "SELF", owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(repo.get).not.toHaveBeenCalled();
    const failure = failed("payroll_payslip_not_found");
    repo.get.mockResolvedValue(failure);
    expect(await new LoadPayrollPayslip(repo).execute(access, payslipId(), "SELF", signal())).toBe(
      failure,
    );
  });
  it.each(["saved", "failed", "aborted", "thrown"])(
    "clears owned PDF bytes after a %s handoff",
    async (outcome) => {
      const repo = repository(),
        raw = payslipPdf(),
        owner = new AbortController();
      repo.pdf.mockResolvedValue(success(raw));
      const files = {
        downloadBinary: vi
          .fn<FileRepository["downloadBinary"]>()
          .mockImplementation(async (file) => {
            expect(file.name).toBe(`payslip-${payslipId()}-en.pdf`);
            assert(file.parts[0]);
            expect(new Uint8Array(file.parts[0])[0]).toBe(37);
            if (outcome === "aborted") {
              owner.abort();
              owner.signal.throwIfAborted();
            }
            if (outcome === "thrown") throw new Error("File adapter failed");
            return outcome === "failed" ? failed("file_download_failed") : success(undefined);
          }),
      };
      const operation = new DownloadPayrollPayslip(repo, files).execute(
        access,
        payslipId(),
        "SELF",
        "EN",
        owner.signal,
      );
      if (outcome === "aborted" || outcome === "thrown")
        await expect(operation).rejects.toBeDefined();
      else expect((await operation).ok).toBe(outcome === "saved");
      expect(new Uint8Array(raw.parts[0]).every((value) => value === 0)).toBe(true);
      expect(repo.pdf).toHaveBeenCalledOnce();
      expect(files.downloadBinary).toHaveBeenCalledOnce();
    },
  );
  it("does not save PDF bytes acquired after their owner is cancelled", async () => {
    const repo = repository(),
      raw = payslipPdf(),
      owner = new AbortController();
    repo.pdf.mockImplementation(async () => {
      owner.abort();
      return success(raw);
    });
    const files = { downloadBinary: vi.fn<FileRepository["downloadBinary"]>() };
    await expect(
      new DownloadPayrollPayslip(repo, files).execute(
        access,
        payslipId(),
        "SELF",
        "ID",
        owner.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(files.downloadBinary).not.toHaveBeenCalled();
    expect(new Uint8Array(raw.parts[0]).every((value) => value === 0)).toBe(true);
  });
});
