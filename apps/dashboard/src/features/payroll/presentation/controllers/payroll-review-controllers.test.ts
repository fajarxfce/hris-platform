import { assert, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  payrollReviewDetails,
  payrollReviewPreparation,
  reviewId,
} from "../../../../../tests/fixtures/payroll-reviews";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  toPayrollReviewDetails,
  toPayrollReviewPreparation,
} from "../../data/mappers/payroll-review-mapper";
import type { PayrollReviewDetails } from "../../domain/entities/payroll-review-details";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollReviewActionController } from "./payroll-review-action-controller";
import { PayrollReviewController } from "./payroll-review-controller";
import { PayrollReviewSubmissionController } from "./payroll-review-submission-controller";
import { PayrollReviewsController } from "./payroll-reviews-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.calculate", "payroll.review"],
};
function gate<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((ok, no) => {
    resolve = ok;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function flush() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}
function actions() {
  const raw = payrollReviewDetails();
  raw.availableActions = ["APPROVE", "REJECT", "WITHDRAW"];
  const details = toPayrollReviewDetails(raw, access.companyId, reviewId),
    preparation = toPayrollReviewPreparation(
      payrollReviewPreparation(),
      access.companyId,
      calculationId,
    );
  return {
    loadReviews: {
      execute: vi
        .fn<PayrollUseCases["loadReviews"]["execute"]>()
        .mockResolvedValue(success({ preparation, reviews: [] })),
    },
    loadReview: {
      execute: vi
        .fn<PayrollUseCases["loadReview"]["execute"]>()
        .mockResolvedValue(success(details)),
    },
    loadReviewAction: {
      execute: vi
        .fn<PayrollUseCases["loadReviewAction"]["execute"]>()
        .mockResolvedValue(success(details)),
    },
    loadReviewSubmission: {
      execute: vi
        .fn<PayrollUseCases["loadReviewSubmission"]["execute"]>()
        .mockResolvedValue(success(preparation)),
    },
    submitReview: {
      execute: vi
        .fn<PayrollUseCases["submitReview"]["execute"]>()
        .mockImplementation(async (_a, _r, _o, input) => success({ id: input.id, version: 0 })),
    },
    decideReview: {
      execute: vi
        .fn<PayrollUseCases["decideReview"]["execute"]>()
        .mockResolvedValue(success({ id: reviewId, version: 1 })),
    },
    withdrawReview: {
      execute: vi
        .fn<PayrollUseCases["withdrawReview"]["execute"]>()
        .mockResolvedValue(success({ id: reviewId, version: 1 })),
    },
  };
}
function scenario(action: "SUBMIT" | "APPROVE" | "REJECT" | "WITHDRAW") {
  const api = actions();
  let index = 0;
  const next = () => `ed900000-0000-4000-8000-${String(++index).padStart(12, "0")}`;
  if (action === "SUBMIT")
    return {
      api,
      controller: new PayrollReviewSubmissionController(api, access, calculationId, next),
      command: api.submitReview.execute,
    };
  return {
    api,
    controller: new PayrollReviewActionController(api, access, reviewId, action, next),
    command: action === "WITHDRAW" ? api.withdrawReview.execute : api.decideReview.execute,
  };
}
describe("review command ownership", () => {
  it.each(["SUBMIT", "APPROVE", "REJECT", "WITHDRAW"] as const)(
    "%s retains the original command through lost responses, MFA and later conflicts",
    async (action) => {
      const { api, controller, command } = scenario(action),
        held = gate<Result<MutationReceipt>>();
      command.mockReturnValueOnce(held.promise);
      controller.activate();
      await flush();
      const pending = controller.save("Reviewed payroll");
      await controller.save("Double click");
      expect(command).toHaveBeenCalledTimes(1);
      held.resolve(failed("connection_unavailable"));
      await pending;
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
      await controller.refresh();
      expect(
        api.loadReviewAction.execute.mock.calls.length +
          api.loadReviewSubmission.execute.mock.calls.length,
      ).toBe(1);
      command.mockResolvedValueOnce(failed("mfa_required"));
      await controller.retry();
      expect(controller.getSnapshot().review).toBeNull();
      command.mockResolvedValueOnce(failed("approval_changed"));
      await controller.retry();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
      await controller.retry();
      expect(controller.getSnapshot().stage).toBe("saved");
      for (const call of command.mock.calls.slice(1))
        expect(call.slice(0, 4)).toEqual(command.mock.calls[0]?.slice(0, 4));
      controller.deactivate();
    },
  );
  it.each(["SUBMIT", "APPROVE", "REJECT", "WITHDRAW"] as const)(
    "%s discards late acknowledgements and failures after scope disposal",
    async (action) => {
      const { controller, command } = scenario(action),
        held = gate<Result<MutationReceipt>>();
      command.mockReturnValueOnce(held.promise);
      controller.activate();
      await flush();
      const pending = controller.save("Review");
      controller.deactivate();
      expect(command.mock.calls[0]?.[4].aborted).toBe(true);
      held.resolve(success({ id: reviewId, version: 1 }));
      await pending;
      expect(controller.getSnapshot().receipt).toBeNull();
      expect(controller.getSnapshot().stage).toBe("loading");
    },
  );
  it("reloads both independent versions before a new decision after a definite reassignment conflict", async () => {
    const { api, controller, command } = scenario("APPROVE");
    controller.activate();
    await flush();
    command.mockResolvedValueOnce(failed("approval_changed"));
    await controller.save("");
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save("");
    expect(command).toHaveBeenCalledTimes(1);
    const raw = payrollReviewDetails();
    raw.approval.version = 3;
    raw.availableActions = ["APPROVE", "REJECT"];
    api.loadReviewAction.execute.mockResolvedValueOnce(
      success(toPayrollReviewDetails(raw, access.companyId, reviewId)),
    );
    await controller.refresh();
    await controller.save("");
    expect(command.mock.calls[1]?.[3]).toMatchObject({
      expectedVersion: 0,
      expectedApprovalVersion: 3,
    });
    expect(command.mock.calls[0]?.[2]).not.toBe(command.mock.calls[1]?.[2]);
    controller.deactivate();
  });
  it.each(["SUBMIT", "REJECT", "WITHDRAW"] as const)(
    "%s requires a reason before allocating a command",
    async (action) => {
      const { controller, command } = scenario(action);
      controller.activate();
      await flush();
      await controller.save("   ");
      expect(command).not.toHaveBeenCalled();
      expect(controller.getSnapshot().failure?.fields.reason).toBe("invalid");
      controller.deactivate();
    },
  );
  it("clears private context on a definite denial without converting it into an unknown outcome", async () => {
    const { controller, command } = scenario("APPROVE");
    controller.activate();
    await flush();
    command.mockResolvedValueOnce(failed("not_assigned_approver"));
    await controller.save("");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    controller.deactivate();
  });
  it("never acts on an unavailable action hint", async () => {
    const { api, controller, command } = scenario("APPROVE"),
      raw = payrollReviewDetails();
    raw.availableActions = [];
    api.loadReviewAction.execute.mockResolvedValue(
      success(toPayrollReviewDetails(raw, access.companyId, reviewId)),
    );
    controller.activate();
    await flush();
    await controller.save("");
    expect(command).not.toHaveBeenCalled();
    controller.deactivate();
  });
});
describe("independent review reads", () => {
  it("cancels a superseded read and ignores its late error", async () => {
    const api = actions(),
      held = gate<Result<PayrollReviewDetails>>();
    api.loadReview.execute.mockReturnValueOnce(held.promise);
    const controller = new PayrollReviewController(api.loadReview, access, reviewId);
    controller.activate();
    await controller.refresh();
    expect(api.loadReview.execute.mock.calls[0]?.[2].aborted).toBe(true);
    held.reject(new Error("private old scope"));
    await flush();
    expect(controller.getSnapshot().data?.review.id).toBe(reviewId);
    expect(controller.getSnapshot().failure).toBeNull();
    controller.deactivate();
  });
  it("withdraws data on access loss and keeps overview/detail acquisition independent", async () => {
    const api = actions(),
      controller = new PayrollReviewController(api.loadReview, access, reviewId),
      overview = new PayrollReviewsController(api.loadReviews, access, calculationId);
    overview.activate();
    await flush();
    expect(api.loadReview.execute).not.toHaveBeenCalled();
    controller.activate();
    await flush();
    assert(controller.getSnapshot().data);
    api.loadReview.execute.mockResolvedValueOnce(failed("access_denied"));
    await controller.refresh();
    expect(controller.getSnapshot().data).toBeNull();
    controller.deactivate();
    overview.deactivate();
    expect(overview.getSnapshot().data).toBeNull();
  });
});
