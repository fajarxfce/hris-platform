import { describe, expect, it, vi } from "vitest";
import {
  amendmentPublicationDetails,
  amendmentPublicationId,
  approvedAmendmentReview,
} from "../../../../../tests/fixtures/payroll-amendment-publications";
import { amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentPublicationDetails } from "../../data/mappers/payroll-amendment-publication-mapper";
import { toPayrollAmendmentReviewDetails } from "../../data/mappers/payroll-amendment-review-mapper";
import type { PayrollAmendmentPublicationPreparation } from "../../domain/entities/payroll-amendment-publication-preparation";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollAmendmentPublicationRecoveryController } from "./payroll-amendment-publication-recovery-controller";
import { PayrollAmendmentPublicationStartController } from "./payroll-amendment-publication-start-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.amend.publish"],
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
function fixture(action: "START" | "RECOVER") {
  const raw = approvedAmendmentReview(),
    review = toPayrollAmendmentReviewDetails(raw, access.companyId, raw.review.id),
    preparation: PayrollAmendmentPublicationPreparation = {
      amendment: review.amendment,
      review: review.review,
      calculation: review.calculation,
      approvalVersion: review.approval.version,
      number: 1,
    },
    exhausted = toPayrollAmendmentPublicationDetails(
      amendmentPublicationDetails("EXHAUSTED"),
      access.companyId,
      amendmentId(),
    );
  const actions = {
    reviewAmendmentPublication: {
      execute: vi
        .fn<PayrollUseCases["reviewAmendmentPublication"]["execute"]>()
        .mockResolvedValue(success(preparation)),
    },
    reviewAmendmentPublicationRecovery: {
      execute: vi
        .fn<PayrollUseCases["reviewAmendmentPublicationRecovery"]["execute"]>()
        .mockResolvedValue(success(exhausted)),
    },
    startAmendmentPublication: {
      execute: vi
        .fn<PayrollUseCases["startAmendmentPublication"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 4 })),
    },
    recoverAmendmentPublication: {
      execute: vi
        .fn<PayrollUseCases["recoverAmendmentPublication"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 5 })),
    },
  };
  let index = 100;
  const identifier = vi.fn(() => amendmentPublicationId(++index));
  const controller =
      action === "START"
        ? new PayrollAmendmentPublicationStartController(actions, access, amendmentId(), identifier)
        : new PayrollAmendmentPublicationRecoveryController(
            actions,
            access,
            amendmentId(),
            amendmentPublicationId(),
            identifier,
          ),
    command =
      action === "START"
        ? actions.startAmendmentPublication.execute
        : actions.recoverAmendmentPublication.execute,
    load =
      action === "START"
        ? actions.reviewAmendmentPublication.execute
        : actions.reviewAmendmentPublicationRecovery.execute;
  return {
    actions,
    preparation,
    exhausted,
    identifier,
    controller,
    command,
    load,
    calls: () =>
      command.mock.calls.map((call) => ({
        scope: call.slice(0, -1),
        input: call.at(-2),
        signal: call.at(-1) as AbortSignal,
      })),
  };
}
describe("publication commands own an immutable operation", () => {
  for (const action of ["START", "RECOVER"] as const) {
    it(`${action} keeps the original operation through lost response, MFA, conflict and revoked access`, async () => {
      const f = fixture(action),
        first = gate<Result<MutationReceipt>>();
      f.command
        .mockReturnValueOnce(first.promise)
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockResolvedValueOnce(failed("payroll_amendment_publication_changed"))
        .mockResolvedValueOnce(failed("access_denied"));
      f.controller.activate();
      f.controller.activate();
      await flush();
      const pending = f.controller.save("  Original reviewed reason  ");
      await f.controller.save("Duplicate");
      await f.controller.retry();
      expect(f.command).toHaveBeenCalledTimes(1);
      first.resolve(failed("connection_unavailable"));
      await pending;
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      await f.controller.refresh();
      await f.controller.save("Replacement");
      expect(f.load).toHaveBeenCalledTimes(1);
      for (let retry = 0; retry < 3; retry++) {
        await f.controller.retry();
        expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
      }
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        receipt: { id: amendmentId(), version: action === "START" ? 4 : 5 },
      });
      if (action === "START")
        expect(f.controller.getSnapshot()).toMatchObject({
          publicationId: amendmentPublicationId(102),
        });
      expect(f.identifier).toHaveBeenCalledTimes(action === "START" ? 2 : 1);
      expect(f.command).toHaveBeenCalledTimes(5);
      for (const call of f.calls()) expect(call.scope).toEqual(f.calls()[0]?.scope);
      expect(f.calls()[0]?.input).toMatchObject({
        reason: "Original reviewed reason",
        expectedVersion: action === "START" ? 3 : 4,
      });
      expect(Object.isFrozen(f.calls()[0]?.input)).toBe(true);
      f.controller.deactivate();
    });
    it(`${action} requires an explicit new review after a definite conflict`, async () => {
      const f = fixture(action);
      f.controller.activate();
      await flush();
      f.command.mockResolvedValueOnce(failed("stale_version"));
      await f.controller.save("Old basis");
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
      await f.controller.save("Duplicate");
      await f.controller.retry();
      expect(f.command).toHaveBeenCalledTimes(1);
      if (action === "START")
        f.actions.reviewAmendmentPublication.execute.mockResolvedValueOnce(
          success({
            ...f.preparation,
            amendment: { ...f.preparation.amendment, version: 8 },
            review: { ...f.preparation.review, version: 2 },
            approvalVersion: 3,
          }),
        );
      else
        f.actions.reviewAmendmentPublicationRecovery.execute.mockResolvedValueOnce(
          success({
            ...f.exhausted,
            amendment: { ...f.exhausted.amendment, version: 8 },
            job: { ...f.exhausted.job, version: 4 },
          }),
        );
      await f.controller.refresh();
      await f.controller.save("New reviewed basis");
      expect(f.calls()[1]?.input).toMatchObject({
        expectedVersion: 8,
        reason: "New reviewed basis",
      });
      expect(f.calls()[1]?.scope).not.toEqual(f.calls()[0]?.scope);
      f.controller.deactivate();
    });
    it(`${action} clears private details on rejection and retains unexpected ambiguous outcomes`, async () => {
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
      await f.controller.refresh();
      f.command.mockRejectedValueOnce(new Error("PRIVATE diagnostic"));
      await f.controller.save("Reviewed");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        failure: { code: "unexpected_error" },
      });
      expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
      f.controller.deactivate();
    });
    it.each(["receipt", "error"])(
      `${action} aborts and discards a late %s after departure`,
      async (late) => {
        const f = fixture(action),
          waiting = gate<Result<MutationReceipt>>();
        f.command.mockReturnValueOnce(waiting.promise);
        f.controller.activate();
        await flush();
        const pending = f.controller.save("Reviewed"),
          listener = vi.fn(),
          unsubscribe = f.controller.subscribe(listener);
        unsubscribe();
        f.controller.deactivate();
        expect(f.calls()[0]?.signal.aborted).toBe(true);
        if (late === "receipt") waiting.resolve(success({ id: amendmentId(), version: 5 }));
        else waiting.reject(new Error("Late private failure"));
        await pending;
        expect(f.controller.getSnapshot()).toMatchObject({
          stage: "loading",
          review: null,
          receipt: null,
          operationId: null,
        });
        expect(listener).not.toHaveBeenCalled();
      },
    );
    it(`${action} handles identifier generation failure before any write`, async () => {
      const f = fixture(action);
      f.controller.activate();
      await flush();
      f.identifier.mockImplementationOnce(() => {
        throw new Error("Unavailable");
      });
      await f.controller.save("Reviewed");
      expect(f.command).not.toHaveBeenCalled();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "reviewing",
        failure: { code: "unexpected_error" },
      });
      await f.controller.save("Reviewed");
      expect(f.command).toHaveBeenCalledTimes(1);
      f.controller.deactivate();
    });
  }
  it("drops superseded preparation and late exceptions after unmount", async () => {
    const f = fixture("START"),
      first = gate<Result<PayrollAmendmentPublicationPreparation>>();
    f.actions.reviewAmendmentPublication.execute.mockReturnValueOnce(first.promise);
    f.controller.activate();
    await f.controller.refresh();
    expect(f.actions.reviewAmendmentPublication.execute.mock.calls[0]?.[2].aborted).toBe(true);
    first.resolve(failed("access_denied"));
    await flush();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    const last = gate<Result<PayrollAmendmentPublicationPreparation>>();
    f.actions.reviewAmendmentPublication.execute.mockReturnValueOnce(last.promise);
    const pending = f.controller.refresh();
    f.controller.deactivate();
    last.reject(new Error("Late diagnostic"));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      review: null,
      failure: null,
    });
  });
  it("cannot recover after the live recovery hint is removed", async () => {
    const f = fixture("RECOVER");
    f.actions.reviewAmendmentPublicationRecovery.execute.mockResolvedValueOnce(
      success({ ...f.exhausted, availableActions: [] }),
    );
    f.controller.activate();
    await flush();
    await f.controller.save("Duplicate recovery");
    expect(f.command).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
