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
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentCalculation } from "../../data/mappers/payroll-amendment-calculation-mapper";
import {
  toPayrollAmendment,
  toPayrollAmendmentDetails,
  toPayrollAmendmentSummary,
} from "../../data/mappers/payroll-amendment-mapper";
import type { PayrollAmendmentSearch } from "../entities/payroll-amendment-search";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import { ListPayrollAmendments } from "./list-payroll-amendments";
import { LoadPayrollAmendment } from "./load-payroll-amendment";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend"],
  },
  signal = new AbortController().signal,
  selection = { revision: null, after: null },
  search: PayrollAmendmentSearch = {
    year: 2026,
    query: "",
    employeeId: null,
    status: null,
    after: null,
  };
function repository() {
  return {
    save: vi.fn<PayrollAmendmentRepository["save"]>(),
    calculate: vi.fn<PayrollAmendmentRepository["calculate"]>(),
    cancel: vi.fn<PayrollAmendmentRepository["cancel"]>(),
    list: vi
      .fn<PayrollAmendmentRepository["list"]>()
      .mockResolvedValue(success({ items: [], nextCursor: null })),
    get: vi
      .fn<PayrollAmendmentRepository["get"]>()
      .mockResolvedValue(
        success(toPayrollAmendmentDetails(amendmentDetails(), access.companyId, amendmentId())),
      ),
    revision: vi
      .fn<PayrollAmendmentRepository["revision"]>()
      .mockResolvedValue(
        success(toPayrollAmendment(amendmentRevision(0), access.companyId, amendmentId(), 0)),
      ),
    history: vi
      .fn<PayrollAmendmentRepository["history"]>()
      .mockResolvedValue(success({ items: [], nextCursor: null })),
    calculation: vi
      .fn<PayrollAmendmentRepository["calculation"]>()
      .mockResolvedValue(
        success(
          toPayrollAmendmentCalculation(
            amendmentCalculation(),
            access.companyId,
            amendmentId(),
            amendmentCalculationId,
          ),
        ),
      ),
  };
}
describe("amendment read policy", () => {
  it.each(["payroll.amend", "payroll.amend.review", "payroll.amend.publish"])(
    "allows independent %s scope",
    async (permission) => {
      const repo = repository(),
        actor = { ...access, permissions: [permission] };
      expect((await new ListPayrollAmendments(repo).execute(actor, search, signal)).ok).toBe(true);
      expect(
        (await new LoadPayrollAmendment(repo).execute(actor, amendmentId(), selection, signal)).ok,
      ).toBe(true);
    },
  );
  it.each(["payroll.read", "payroll.self.read", "payroll.review", "payroll.pay", "company.manage"])(
    "does not widen %s",
    async (permission) => {
      const repo = repository(),
        actor = { ...access, permissions: [permission] };
      expect(await new ListPayrollAmendments(repo).execute(actor, search, signal)).toEqual(
        failed("access_denied"),
      );
      expect(
        await new LoadPayrollAmendment(repo).execute(actor, amendmentId(), selection, signal),
      ).toEqual(failed("access_denied"));
      expect(repo.list).not.toHaveBeenCalled();
      expect(repo.get).not.toHaveBeenCalled();
    },
  );
  it.each<Partial<PayrollAmendmentSearch>>([
    { year: 2023 },
    { year: 2101 },
    { year: Number.NaN },
    { query: "x".repeat(121) },
    { query: "a\nb" },
    { employeeId: "wrong" },
    { after: "wrong" },
  ])("rejects invalid directory bounds %j", async (input) => {
    const repo = repository();
    expect(
      await new ListPayrollAmendments(repo).execute(access, { ...search, ...input }, signal),
    ).toEqual(failed("invalid_page"));
    expect(repo.list).not.toHaveBeenCalled();
  });
  it("normalizes safe identifiers and literal search without authorizing a new scope", async () => {
    const repo = repository();
    await new ListPayrollAmendments(repo).execute(
      access,
      { ...search, after: amendmentId().toUpperCase(), query: "  A%_ B  " },
      signal,
    );
    expect(repo.list).toHaveBeenCalledWith(
      access.companyId,
      { ...search, query: "A%_ B", after: amendmentId() },
      signal,
    );
  });
  it.each([Number.NaN, -1, 1000, 1.5])(
    "invalid revision %s never reads the current record",
    async (revision) => {
      const repo = repository();
      expect(
        await new LoadPayrollAmendment(repo).execute(
          access,
          amendmentId(),
          { revision, after: null },
          signal,
        ),
      ).toEqual(failed("invalid_page"));
      expect(repo.get).not.toHaveBeenCalled();
    },
  );
  it("uses the retained proposal's own calculation pointer", async () => {
    const repo = repository(),
      result = await new LoadPayrollAmendment(repo).execute(
        access,
        amendmentId().toUpperCase(),
        { revision: 0, after: null },
        signal,
      );
    assert(result.ok);
    expect(result.value.selected.summary.version).toBe(0);
    expect(result.value.current.amendment.summary.version).toBe(1);
    expect(result.value.calculation).toBeNull();
    expect(repo.revision).toHaveBeenCalledWith(access.companyId, amendmentId(), 0, signal);
    expect(repo.calculation).not.toHaveBeenCalled();
  });
  it("bounds history to the aggregate observed by this read", async () => {
    const repo = repository();
    repo.get.mockResolvedValue(
      success(toPayrollAmendmentDetails(amendmentDetails(30), access.companyId, amendmentId())),
    );
    repo.history.mockResolvedValue(
      success({
        items: Array.from({ length: 25 }, (_, i) =>
          toPayrollAmendmentSummary(amendmentSummary(i + 25), access.companyId),
        ),
        nextCursor: "49",
      }),
    );
    const result = await new LoadPayrollAmendment(repo).execute(
      access,
      amendmentId(),
      { revision: null, after: 24 },
      signal,
    );
    assert(result.ok);
    expect(result.value.history.items.map((row) => row.version)).toEqual([25, 26, 27, 28, 29, 30]);
    expect(result.value.history.nextCursor).toBeNull();
  });
  it.each(["get", "revision", "history", "calculation"] as const)(
    "preserves a denied %s without returning a partial private result",
    async (stage) => {
      const repo = repository();
      repo[stage].mockResolvedValue(failed("access_denied"));
      expect(
        await new LoadPayrollAmendment(repo).execute(
          access,
          amendmentId(),
          { revision: stage === "revision" ? 0 : null, after: stage === "history" ? 0 : null },
          signal,
        ),
      ).toEqual(failed("access_denied"));
    },
  );
  it("cancellation after acquisition prevents subsequent reads", async () => {
    const repo = repository(),
      abort = new AbortController();
    repo.get.mockImplementation(async () => {
      abort.abort();
      return success(
        toPayrollAmendmentDetails(amendmentDetails(), access.companyId, amendmentId()),
      );
    });
    await expect(
      new LoadPayrollAmendment(repo).execute(
        access,
        amendmentId(),
        { revision: 0, after: null },
        abort.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(repo.revision).not.toHaveBeenCalled();
    expect(repo.calculation).not.toHaveBeenCalled();
  });
});
