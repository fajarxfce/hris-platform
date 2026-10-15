import { describe, expect, it, vi } from "vitest";
import {
  amendmentReviewDetails,
  amendmentReviewId,
} from "../../../../../tests/fixtures/payroll-amendment-reviews";
import { amendmentDetails, amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import { toPayrollAmendmentReviewDetails } from "../../data/mappers/payroll-amendment-review-mapper";
import type { PayrollAmendmentReviewDetails } from "../../domain/entities/payroll-amendment-review-details";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollAmendmentReader } from "./payroll-amendment-reader";
import { PayrollAmendmentReviewActionController } from "./payroll-amendment-review-action-controller";
import { PayrollAmendmentReviewSubmissionController } from "./payroll-amendment-review-submission-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.amend", "payroll.amend.review"],
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
function fixture(action: "SUBMIT" | "APPROVE" | "REJECT" | "WITHDRAW") {
  const current = toPayrollAmendmentDetails(amendmentDetails(), access.companyId, amendmentId()),
    details = toPayrollAmendmentReviewDetails(
      amendmentReviewDetails(),
      access.companyId,
      amendmentReviewId(),
    );
  const actions = {
    loadAmendmentReviewSubmission: {
      execute: vi
        .fn<PayrollUseCases["loadAmendmentReviewSubmission"]["execute"]>()
        .mockResolvedValue(success(current)),
    },
    loadAmendmentReviewAction: {
      execute: vi
        .fn<PayrollUseCases["loadAmendmentReviewAction"]["execute"]>()
        .mockResolvedValue(
          success({ ...details, availableActions: ["APPROVE", "REJECT", "WITHDRAW"] }),
        ),
    },
    submitAmendmentReview: {
      execute: vi
        .fn<PayrollUseCases["submitAmendmentReview"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 2 })),
    },
    decideAmendmentReview: {
      execute: vi
        .fn<PayrollUseCases["decideAmendmentReview"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 3 })),
    },
    withdrawAmendmentReview: {
      execute: vi
        .fn<PayrollUseCases["withdrawAmendmentReview"]["execute"]>()
        .mockResolvedValue(success({ id: amendmentId(), version: 3 })),
    },
  };
  let index = 100;
  const identifier = vi.fn(() => amendmentReviewId(++index));
  return {
    current,
    details,
    actions,
    identifier,
    command:
      action === "SUBMIT"
        ? actions.submitAmendmentReview.execute
        : action === "WITHDRAW"
          ? actions.withdrawAmendmentReview.execute
          : actions.decideAmendmentReview.execute,
    load:
      action === "SUBMIT"
        ? actions.loadAmendmentReviewSubmission.execute
        : actions.loadAmendmentReviewAction.execute,
    controller:
      action === "SUBMIT"
        ? new PayrollAmendmentReviewSubmissionController(actions, access, amendmentId(), identifier)
        : new PayrollAmendmentReviewActionController(
            actions,
            access,
            amendmentReviewId(),
            action,
            identifier,
          ),
  };
}
describe("amendment review command ownership", () => {
  for (const action of ["SUBMIT", "APPROVE", "REJECT", "WITHDRAW"] as const) {
    it(`${action} retains its original command through response loss, MFA, competing versions and denied retries`, async () => {
      const f = fixture(action),
        first = gate<Result<MutationReceipt>>();
      f.command
        .mockReturnValueOnce(first.promise)
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockResolvedValueOnce(failed("payroll_amendment_review_not_current"))
        .mockResolvedValueOnce(failed("access_denied"));
      f.controller.activate();
      f.controller.activate();
      await flush();
      const pending = f.controller.save("  Original independent reason  ");
      await f.controller.save("Double tap");
      await f.controller.retry();
      expect(f.command).toHaveBeenCalledTimes(1);
      first.resolve(failed("connection_unavailable"));
      await pending;
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      await f.controller.refresh();
      await f.controller.save("Replaced input");
      expect(f.load).toHaveBeenCalledTimes(1);
      expect(f.identifier).toHaveBeenCalledTimes(action === "SUBMIT" ? 2 : 1);
      for (let retry = 0; retry < 3; retry++) {
        await f.controller.retry();
        expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
      }
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        receipt: { id: amendmentId(), version: action === "SUBMIT" ? 2 : 3 },
      });
      if (action === "SUBMIT")
        expect(f.controller.getSnapshot()).toMatchObject({ reviewId: amendmentReviewId(102) });
      expect(f.command).toHaveBeenCalledTimes(5);
      for (const call of f.command.mock.calls)
        expect(call.slice(0, 4)).toEqual(f.command.mock.calls[0]?.slice(0, 4));
      expect(f.command.mock.calls[0]?.[3]).toMatchObject({
        reason: "Original independent reason",
        expectedVersion: action === "SUBMIT" ? 1 : 2,
      });
      expect(Object.isFrozen(f.command.mock.calls[0]?.[3])).toBe(true);
      f.controller.deactivate();
    });
    it(`${action} requires fresh acquisition after a definite initial version conflict`, async () => {
      const f = fixture(action);
      f.controller.activate();
      await flush();
      f.command.mockResolvedValueOnce(failed("stale_version"));
      await f.controller.save("Old review");
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
      await f.controller.save("Duplicate");
      await f.controller.retry();
      expect(f.command).toHaveBeenCalledTimes(1);
      if (action === "SUBMIT")
        f.actions.loadAmendmentReviewSubmission.execute.mockResolvedValueOnce(
          success({
            ...f.current,
            amendment: {
              ...f.current.amendment,
              summary: { ...f.current.amendment.summary, version: 8 },
            },
          }),
        );
      else
        f.actions.loadAmendmentReviewAction.execute.mockResolvedValueOnce(
          success({
            ...f.details,
            amendment: { ...f.details.amendment, version: 8 },
            review: { ...f.details.review, version: 1 },
            approval: { ...f.details.approval, version: 3 },
            availableActions: [action],
          }),
        );
      await f.controller.refresh();
      await f.controller.save("Current independent reason");
      expect(f.command.mock.calls[1]?.[3]).toMatchObject({
        expectedVersion: 8,
        reason: "Current independent reason",
      });
      if (action !== "SUBMIT")
        expect(f.command.mock.calls[1]?.[3]).toMatchObject({
          expectedReviewVersion: 1,
          expectedApprovalVersion: 3,
        });
      expect(f.command.mock.calls[1]?.[2]).not.toBe(f.command.mock.calls[0]?.[2]);
      f.controller.deactivate();
    });
    it(`${action} clears rejected authority and converts an unexpected lost outcome into explicit recovery`, async () => {
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
      f.command.mockRejectedValueOnce(new Error("PRIVATE connection diagnostic"));
      await f.controller.save("Reviewed");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        failure: { code: "unexpected_error" },
      });
      expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
      f.controller.deactivate();
    });
    it.each(["receipt", "failure"])(
      `${action} aborts on departure and ignores a late %s`,
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
        expect(f.command.mock.calls[0]?.[4].aborted).toBe(true);
        if (late === "receipt") waiting.resolve(success({ id: amendmentId(), version: 3 }));
        else waiting.reject(new Error("Late private response"));
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
    it(`${action} cannot save without a live hint and handles identifier failure before writing`, async () => {
      const f = fixture(action);
      if (action === "SUBMIT")
        f.actions.loadAmendmentReviewSubmission.execute.mockResolvedValueOnce(
          success({ ...f.current, availableActions: [] }),
        );
      else
        f.actions.loadAmendmentReviewAction.execute.mockResolvedValueOnce(
          success({ ...f.details, availableActions: [] }),
        );
      f.controller.activate();
      await flush();
      await f.controller.save("No hint");
      expect(f.command).not.toHaveBeenCalled();
      await f.controller.refresh();
      f.identifier.mockImplementationOnce(() => {
        throw new Error("Private entropy failure");
      });
      await f.controller.save("Reviewed");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "reviewing",
        failure: { code: "unexpected_error" },
      });
      expect(f.command).not.toHaveBeenCalled();
      f.controller.deactivate();
    });
  }
  it("ignores superseded action preflights even when an old read fails after the current one", async () => {
    const f = fixture("APPROVE"),
      waiting = gate<Result<PayrollAmendmentReviewDetails>>();
    f.actions.loadAmendmentReviewAction.execute.mockReturnValueOnce(waiting.promise);
    f.controller.activate();
    await f.controller.refresh();
    expect(f.actions.loadAmendmentReviewAction.execute.mock.calls[0]?.[3].aborted).toBe(true);
    waiting.reject(new Error("Late read"));
    await flush();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    f.controller.deactivate();
  });
});
describe("scoped amendment review reader", () => {
  it("cancels replaced reads and clears private data after denial or disposal", async () => {
    const f = fixture("APPROVE"),
      waiting = gate<Result<PayrollAmendmentReviewDetails>>(),
      load = {
        execute: vi
          .fn<PayrollUseCases["loadAmendmentReview"]["execute"]>()
          .mockResolvedValue(success(f.details)),
      },
      reader = new PayrollAmendmentReader(load, access, amendmentReviewId());
    load.execute.mockReturnValueOnce(waiting.promise);
    reader.activate();
    reader.activate();
    await reader.refresh();
    expect(load.execute.mock.calls[0]?.[2].aborted).toBe(true);
    waiting.resolve(success({ ...f.details, accounts: [] }));
    await flush();
    expect(reader.getSnapshot().data).toBe(f.details);
    load.execute.mockResolvedValueOnce(failed("access_denied"));
    await reader.refresh();
    expect(reader.getSnapshot()).toMatchObject({ stage: "unavailable", data: null });
    await reader.refresh();
    expect(reader.getSnapshot().data).toBe(f.details);
    const late = gate<Result<PayrollAmendmentReviewDetails>>();
    load.execute.mockReturnValueOnce(late.promise);
    const pending = reader.refresh();
    reader.deactivate();
    expect(load.execute.mock.calls.at(-1)?.[2].aborted).toBe(true);
    late.reject(new Error("Late private failure"));
    await pending;
    expect(reader.getSnapshot()).toMatchObject({ stage: "loading", data: null, failure: null });
  });
  it("does not iterate new subscribers during the same publication", async () => {
    const f = fixture("APPROVE"),
      load = {
        execute: vi
          .fn<PayrollUseCases["loadAmendmentReview"]["execute"]>()
          .mockResolvedValue(success(f.details)),
      },
      reader = new PayrollAmendmentReader(load, access, amendmentReviewId()),
      added = vi.fn();
    const releases: (() => void)[] = [];
    releases.push(
      reader.subscribe(() => {
        releases.push(reader.subscribe(added));
      }),
    );
    reader.activate();
    expect(added).not.toHaveBeenCalled();
    await flush();
    expect(added).toHaveBeenCalledTimes(1);
    for (const release of releases) release();
    reader.deactivate();
  });
});
