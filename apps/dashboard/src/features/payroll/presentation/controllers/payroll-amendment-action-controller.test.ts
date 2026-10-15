import { describe, expect, it, vi } from "vitest";
import { amendmentDetails, amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import type { PayrollAmendmentDetails } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentDraftAction } from "../../domain/entities/payroll-amendment-command";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollAmendmentActionController } from "./payroll-amendment-action-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.amend"],
};
function gate<T>() {
  let resolve!: (value: T) => void, reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function flush() {
  await Promise.resolve();
  await Promise.resolve();
}
function fixture(action: PayrollAmendmentDraftAction = "CALCULATE") {
  const details = toPayrollAmendmentDetails(amendmentDetails(0), access.companyId, amendmentId());
  const actions = {
    loadAmendmentAction: {
      execute: vi
        .fn<PayrollUseCases["loadAmendmentAction"]["execute"]>()
        .mockResolvedValue(success(details)),
    },
    calculateAmendment: {
      execute: vi
        .fn<PayrollUseCases["calculateAmendment"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 1 })),
    },
    cancelAmendment: {
      execute: vi
        .fn<PayrollUseCases["cancelAmendment"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 1 })),
    },
  };
  let next = 100;
  const identifier = vi.fn(() => amendmentId(++next));
  return {
    details,
    actions,
    identifier,
    command:
      action === "CALCULATE" ? actions.calculateAmendment.execute : actions.cancelAmendment.execute,
    controller: new PayrollAmendmentActionController(
      actions,
      access,
      amendmentId(),
      action,
      identifier,
    ),
  };
}
describe("amendment action lifecycle", () => {
  for (const action of ["CALCULATE", "CANCEL"] as const) {
    it(`${action} retains the original operation through response loss, MFA, conflicts and denial`, async () => {
      const f = fixture(action),
        first = gate<Result<MutationReceipt>>();
      f.command
        .mockReturnValueOnce(first.promise)
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockResolvedValueOnce(failed("stale_version"))
        .mockResolvedValueOnce(failed("access_denied"));
      f.controller.activate();
      f.controller.activate();
      await flush();
      const pending = f.controller.save("  Original reason  ");
      await f.controller.save("Double tap");
      expect(f.command).toHaveBeenCalledTimes(1);
      first.resolve(failed("connection_unavailable"));
      await pending;
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      await f.controller.refresh();
      await f.controller.save("Changed input");
      expect(f.actions.loadAmendmentAction.execute).toHaveBeenCalledTimes(1);
      expect(f.identifier).toHaveBeenCalledTimes(1);
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
      await f.controller.retry();
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        receipt: { id: amendmentId(), version: 1 },
      });
      expect(f.command).toHaveBeenCalledTimes(5);
      for (const call of f.command.mock.calls)
        expect(call.slice(0, 4)).toEqual(f.command.mock.calls[0]?.slice(0, 4));
      expect(f.command.mock.calls[0]?.[3]).toEqual({
        expectedVersion: 0,
        reason: "Original reason",
      });
      expect(Object.isFrozen(f.command.mock.calls[0]?.[3])).toBe(true);
      f.controller.deactivate();
    });
    it.each([
      "stale_version",
      "payroll_amendment_not_editable",
      "payroll_amendment_sources_changed",
      "payroll_amendment_calculation_limit",
    ])(`${action} blocks after an initial %s until deliberate acquisition`, async (code) => {
      const f = fixture(action);
      f.controller.activate();
      await flush();
      f.command.mockResolvedValueOnce(failed(code));
      await f.controller.save("Reviewed");
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
      await f.controller.save("Duplicate");
      expect(f.command).toHaveBeenCalledTimes(1);
      f.actions.loadAmendmentAction.execute.mockResolvedValueOnce(
        success({
          ...f.details,
          amendment: {
            ...f.details.amendment,
            summary: { ...f.details.amendment.summary, version: 8 },
          },
        }),
      );
      await f.controller.refresh();
      await f.controller.save("Current revision");
      expect(f.command.mock.calls[1]?.[3]).toEqual({
        expectedVersion: 8,
        reason: "Current revision",
      });
      expect(f.command.mock.calls[1]?.[2]).not.toBe(f.command.mock.calls[0]?.[2]);
      f.controller.deactivate();
    });
    it(`${action} clears a denied review and releases a definitively rejected operation`, async () => {
      const f = fixture(action);
      f.controller.activate();
      await flush();
      f.command.mockResolvedValueOnce(failed("access_denied"));
      await f.controller.save("Private reason");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        review: null,
        operationId: null,
      });
      await f.controller.retry();
      expect(f.command).toHaveBeenCalledTimes(1);
      f.controller.deactivate();
    });
    it(`${action} aborts pending work and ignores a late committed receipt after disposal`, async () => {
      const f = fixture(action),
        waiting = gate<Result<MutationReceipt>>();
      f.command.mockReturnValueOnce(waiting.promise);
      f.controller.activate();
      await flush();
      const pending = f.controller.save("Reviewed");
      const listener = vi.fn(),
        unsubscribe = f.controller.subscribe(listener);
      unsubscribe();
      f.controller.deactivate();
      expect(f.command.mock.calls[0]?.[4].aborted).toBe(true);
      waiting.resolve(success({ id: amendmentId(), version: 1 }));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        review: null,
        receipt: null,
      });
      expect(listener).not.toHaveBeenCalled();
    });
  }
  it("rejects stale reads after refresh and releases acquisition on disposal", async () => {
    const f = fixture(),
      waiting = gate<Result<PayrollAmendmentDetails>>();
    f.actions.loadAmendmentAction.execute.mockReturnValueOnce(waiting.promise);
    f.controller.activate();
    await f.controller.refresh();
    expect(f.actions.loadAmendmentAction.execute.mock.calls[0]?.[3].aborted).toBe(true);
    waiting.reject(new Error("late private response"));
    await flush();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    f.controller.deactivate();
  });
  it("does not submit unavailable action hints and handles identifier failure locally", async () => {
    const f = fixture();
    f.actions.loadAmendmentAction.execute.mockResolvedValueOnce(
      success({ ...f.details, availableActions: [] }),
    );
    f.controller.activate();
    await flush();
    await f.controller.save("Unavailable");
    expect(f.command).not.toHaveBeenCalled();
    await f.controller.refresh();
    f.identifier.mockImplementationOnce(() => {
      throw new Error("private");
    });
    await f.controller.save("Reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      failure: { code: "unexpected_error" },
    });
    expect(f.command).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
