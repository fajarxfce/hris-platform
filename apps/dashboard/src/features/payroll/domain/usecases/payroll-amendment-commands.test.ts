import { assert, describe, expect, it, vi } from "vitest";
import { amendmentDetails, amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import type { PayrollAmendmentCommand } from "../entities/payroll-amendment-command";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import { CalculatePayrollAmendment } from "./calculate-payroll-amendment";
import { CancelPayrollAmendment } from "./cancel-payroll-amendment";
import { LoadPayrollAmendmentAction } from "./load-payroll-amendment-action";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend"],
  },
  id = amendmentId(),
  operation = amendmentId(10) as OperationId,
  signal = new AbortController().signal;
function fixture() {
  const details = toPayrollAmendmentDetails(amendmentDetails(0), access.companyId, id);
  const repo = {
    get: vi.fn<PayrollAmendmentRepository["get"]>().mockResolvedValue(success(details)),
    calculate: vi
      .fn<PayrollAmendmentRepository["calculate"]>()
      .mockResolvedValue(success({ id, version: 1 })),
    cancel: vi
      .fn<PayrollAmendmentRepository["cancel"]>()
      .mockResolvedValue(success({ id, version: 1 })),
    save: vi.fn<PayrollAmendmentRepository["save"]>(),
    list: vi.fn<PayrollAmendmentRepository["list"]>(),
    history: vi.fn<PayrollAmendmentRepository["history"]>(),
    revision: vi.fn<PayrollAmendmentRepository["revision"]>(),
    calculation: vi.fn<PayrollAmendmentRepository["calculation"]>(),
  };
  return { details, repo };
}
describe("amendment action authority and original command", () => {
  for (const action of ["CALCULATE", "CANCEL"] as const) {
    const useCase = (repo: PayrollAmendmentRepository) =>
      action === "CALCULATE"
        ? new CalculatePayrollAmendment(repo)
        : new CancelPayrollAmendment(repo);
    const method = action === "CALCULATE" ? "calculate" : "cancel";
    it(`${action} reviews current action eligibility with its independent grant`, async () => {
      const f = fixture(),
        load = new LoadPayrollAmendmentAction(f.repo);
      expect((await load.execute(access, id, action, signal)).ok).toBe(true);
      f.repo.get.mockResolvedValueOnce(success({ ...f.details, availableActions: [] }));
      expect(await load.execute(access, id, action, signal)).toEqual(
        failed("payroll_amendment_not_editable"),
      );
      f.repo.get.mockResolvedValueOnce(failed("mfa_required"));
      expect(await load.execute(access, id, action, signal)).toEqual(failed("mfa_required"));
    });
    it.each(["payroll.read", "payroll.calculate", "payroll.amend.review", "payroll.amend.publish"])(
      `${action} excludes %s before I/O`,
      async (permission) => {
        const f = fixture(),
          other = { ...access, permissions: [permission] };
        expect(
          await new LoadPayrollAmendmentAction(f.repo).execute(other, id, action, signal),
        ).toEqual(failed("access_denied"));
        expect(
          await useCase(f.repo).execute(
            other,
            id,
            operation,
            { expectedVersion: 0, reason: "Reviewed" },
            signal,
          ),
        ).toEqual(failed("access_denied"));
        expect(f.repo.get).not.toHaveBeenCalled();
        expect(f.repo[method]).not.toHaveBeenCalled();
      },
    );
    it.each([
      { expectedVersion: -1 },
      { expectedVersion: 999 },
      { expectedVersion: 0.5 },
      { expectedVersion: null },
      { reason: " " },
      { reason: "x".repeat(1001) },
      { reason: "reason\nline" },
    ])(`${action} bounds %j before the command`, async (invalid) => {
      const f = fixture(),
        input = { expectedVersion: 0, reason: "Reviewed", ...invalid } as PayrollAmendmentCommand;
      const result = await useCase(f.repo).execute(access, id, operation, input, signal);
      expect(result.ok).toBe(false);
      expect(f.repo[method]).not.toHaveBeenCalled();
    });
    it(`${action} sends its frozen observed command without rereading current state`, async () => {
      const f = fixture(),
        actionUseCase = useCase(f.repo),
        input = { expectedVersion: 0, reason: "  Reviewed payroll  " };
      const first = await actionUseCase.execute(access, id.toUpperCase(), operation, input, signal);
      assert(first.ok);
      expect(first.value.version).toBe(1);
      f.repo.get.mockResolvedValueOnce(failed("payroll_amendment_not_editable"));
      await actionUseCase.execute(access, id, operation, input, signal);
      expect(f.repo[method]).toHaveBeenCalledTimes(2);
      expect(f.repo.get).not.toHaveBeenCalled();
      expect(f.repo[method].mock.calls[0]?.slice(0, 4)).toEqual([
        access.companyId,
        id,
        operation,
        { expectedVersion: 0, reason: "Reviewed payroll" },
      ]);
      expect(f.repo[method].mock.calls[1]?.slice(0, 4)).toEqual(
        f.repo[method].mock.calls[0]?.slice(0, 4),
      );
      expect(Object.isFrozen(f.repo[method].mock.calls[0]?.[3])).toBe(true);
      expect(input.reason).toBe("  Reviewed payroll  ");
    });
    it(`${action} rejects malformed identity and cancelled ownership`, async () => {
      const f = fixture(),
        input = { expectedVersion: 0, reason: "Reviewed" };
      expect(await useCase(f.repo).execute(access, "invalid", operation, input, signal)).toEqual(
        failed("invalid_payroll_amendment"),
      );
      expect(
        await useCase(f.repo).execute(access, id, "invalid" as OperationId, input, signal),
      ).toEqual(failed("invalid_payroll_amendment"));
      const owner = new AbortController();
      owner.abort();
      expect(() => useCase(f.repo).execute(access, id, operation, input, owner.signal)).toThrow();
      expect(f.repo[method]).not.toHaveBeenCalled();
    });
  }
  it("cancellation during action review rejects acquired private data", async () => {
    const f = fixture(),
      owner = new AbortController();
    f.repo.get.mockImplementationOnce(async () => {
      owner.abort();
      return success(f.details);
    });
    await expect(
      new LoadPayrollAmendmentAction(f.repo).execute(access, id, "CALCULATE", owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
