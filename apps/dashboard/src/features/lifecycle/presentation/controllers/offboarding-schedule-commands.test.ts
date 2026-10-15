import { describe, expect, it, vi } from "vitest";
import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  scheduleAccess as access,
  scheduleCaseId as caseId,
  deferred,
  scheduleDetails,
  scheduleId,
  scheduleJobId,
  scheduleReview,
} from "../../di/offboarding-schedule-fixture";
import type {
  OffboardingScheduleChange,
  OffboardingScheduleCreation,
} from "../../domain/entities/offboarding-schedule-command";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { OffboardingScheduleChangeController } from "./offboarding-schedule-change-controller";
import { OffboardingScheduleStartController } from "./offboarding-schedule-start-controller";

type Kind = "schedule" | "cancel" | "recover";
function fixture(kind: Kind) {
  const review = vi
    .fn<LifecycleUseCases["loadOffboardingScheduleReview"]["execute"]>()
    .mockResolvedValue(success(scheduleReview));
  const details = vi
    .fn<LifecycleUseCases["loadOffboardingSchedule"]["execute"]>()
    .mockResolvedValue(
      success({ ...scheduleDetails, actions: kind === "recover" ? ["recover"] : ["cancel"] }),
    );
  const write = vi
    .fn<
      (
        access: CompanyAccess,
        operation: OperationId,
        input: OffboardingScheduleCreation | OffboardingScheduleChange,
        signal: AbortSignal,
      ) => Promise<Result<MutationReceipt>>
    >()
    .mockResolvedValue(
      success({
        id: kind === "cancel" ? scheduleJobId : scheduleId,
        version: kind === "schedule" ? 0 : 1,
      }),
    );
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller =
    kind === "schedule"
      ? new OffboardingScheduleStartController(
          {
            loadOffboardingScheduleReview: { execute: review },
            scheduleOffboarding: { execute: write },
          },
          access,
          caseId,
          next,
        )
      : new OffboardingScheduleChangeController(
          { execute: details },
          { execute: write },
          kind,
          access,
          caseId,
          scheduleId,
          next,
        );
  return { controller, review, details, write, next };
}
async function ready(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
}
describe.each(["schedule", "cancel", "recover"] as const)(
  "%s offboarding command ownership",
  (kind) => {
    it("pins reviewed versions, admits one pending write and uses the schedule for navigation", async () => {
      const f = fixture(kind);
      await ready(f);
      const held = deferred<Result<MutationReceipt>>();
      f.write.mockReturnValueOnce(held.promise);
      const pending = f.controller.save(" Reviewed departure ");
      await f.controller.save("Another reason");
      await f.controller.retry();
      await f.controller.refresh();
      expect(f.write).toHaveBeenCalledOnce();
      expect(f.review.mock.calls.length + f.details.mock.calls.length).toBe(1);
      const original = f.write.mock.lastCall?.[2];
      expect(Object.isFrozen(original)).toBe(true);
      expect(original).toMatchObject(
        kind === "schedule"
          ? {
              caseId,
              expectedVersion: 3,
              employmentVersion: 7,
              basisRevision: scheduleReview.basisRevision,
              reason: "Reviewed departure",
            }
          : {
              id: scheduleId,
              caseId,
              jobId: scheduleJobId,
              expectedVersion: 0,
              expectedJobVersion: 0,
              reason: "Reviewed departure",
            },
      );
      held.resolve(
        success({
          id: kind === "cancel" ? scheduleJobId : (original?.id ?? ""),
          version: kind === "schedule" ? 0 : 1,
        }),
      );
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "saved",
        operationId: null,
        review: null,
        scheduleId: original?.id,
      });
      await f.controller.save("Duplicate");
      expect(f.write).toHaveBeenCalledOnce();
      f.controller.deactivate();
    });
    it("retains an uncertain command through MFA and later conflicts while erasing private review", async () => {
      const f = fixture(kind);
      await ready(f);
      f.write
        .mockResolvedValueOnce(failed("connection_unavailable"))
        .mockResolvedValueOnce(failed("mfa_required"))
        .mockResolvedValueOnce(failed("offboarding_schedule_changed"))
        .mockResolvedValueOnce(failed("offboarding_schedule_finished"));
      await f.controller.save("Reviewed departure");
      const captured = f.write.mock.lastCall;
      for (const code of [
        "mfa_required",
        "offboarding_schedule_changed",
        "offboarding_schedule_finished",
      ]) {
        await f.controller.retry();
        expect(f.controller.getSnapshot()).toMatchObject({
          stage: "unconfirmed",
          failure: { code },
          review: null,
          operationId: captured?.[1],
        });
        await f.controller.refresh();
        await f.controller.save("Replacement");
      }
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      for (const call of f.write.mock.calls) {
        expect(call[1]).toBe(captured?.[1]);
        expect(call[2]).toBe(captured?.[2]);
      }
      expect(f.review.mock.calls.length + f.details.mock.calls.length).toBe(1);
      expect(f.next).toHaveBeenCalledTimes(kind === "schedule" ? 2 : 1);
      f.controller.deactivate();
    });
    it.each(["offboarding_schedule_changed", "stale_version", "offboarding_schedule_active"])(
      "a first known %s conflict requires a deliberate new review",
      async (code) => {
        const f = fixture(kind);
        await ready(f);
        f.write.mockResolvedValueOnce(failed(code));
        await f.controller.save("First review");
        expect(f.controller.getSnapshot()).toMatchObject({
          stage: "conflict",
          operationId: null,
          failure: { code },
        });
        await f.controller.save("Not reviewed again");
        expect(f.write).toHaveBeenCalledOnce();
        f.review.mockResolvedValueOnce(
          success({
            ...scheduleReview,
            employmentVersion: 8,
            case: { ...scheduleReview.case, version: 4 },
          }),
        );
        f.details.mockResolvedValueOnce(
          success({
            ...scheduleDetails,
            job: { ...scheduleDetails.job, version: 2 },
            actions: kind === "recover" ? ["recover"] : ["cancel"],
          }),
        );
        await f.controller.refresh();
        await f.controller.save("Second review");
        expect(f.write.mock.lastCall?.[2]).toMatchObject(
          kind === "schedule"
            ? { employmentVersion: 8, expectedVersion: 4 }
            : { expectedJobVersion: 2 },
        );
        expect(f.write.mock.calls[1]?.[1]).not.toBe(f.write.mock.calls[0]?.[1]);
        f.controller.deactivate();
      },
    );
    it("removes inaccessible review and accepts only explicit reload before another command", async () => {
      const f = fixture(kind);
      await ready(f);
      f.write.mockResolvedValueOnce(failed("offboarding_access_required"));
      await f.controller.save("Private reason");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        review: null,
        operationId: null,
      });
      await f.controller.save("Again");
      await f.controller.retry();
      expect(f.write).toHaveBeenCalledOnce();
      await f.controller.refresh();
      expect(f.controller.getSnapshot().stage).toBe("reviewing");
      f.controller.deactivate();
    });
    it.each(["success", "failure"])(
      "disposal aborts and ignores late %s after reactivation",
      async (outcome) => {
        const f = fixture(kind);
        await ready(f);
        const held = deferred<Result<MutationReceipt>>();
        f.write.mockReturnValueOnce(held.promise);
        const pending = f.controller.save("Reviewed departure"),
          signal = f.write.mock.lastCall?.[3];
        const listener = vi.fn(),
          unsubscribe = f.controller.subscribe(listener);
        unsubscribe();
        f.controller.deactivate();
        expect(signal?.aborted).toBe(true);
        await ready(f);
        if (outcome === "success") held.resolve(success({ id: scheduleId, version: 0 }));
        else held.reject(new Error("Late failure"));
        await pending;
        expect(f.controller.getSnapshot()).toMatchObject({
          stage: "reviewing",
          receipt: null,
          failure: null,
        });
        expect(listener).not.toHaveBeenCalled();
        f.controller.deactivate();
      },
    );
    it("sanitizes unexpected command failures and preserves their uncertain identity", async () => {
      const f = fixture(kind);
      await ready(f);
      f.write.mockRejectedValueOnce(new Error("PRIVATE"));
      await f.controller.save("Reviewed");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        failure: { code: "unexpected_error" },
      });
      expect(JSON.stringify(f.controller.getSnapshot().failure)).not.toContain("PRIVATE");
      await f.controller.retry();
      expect(f.controller.getSnapshot().stage).toBe("saved");
      f.controller.deactivate();
    });
  },
);
it("blocked review and unavailable actions never allocate or submit commands", async () => {
  for (const kind of ["schedule", "cancel", "recover"] as const) {
    const f = fixture(kind);
    f.review.mockResolvedValueOnce(
      success({ ...scheduleReview, blockers: ["required_lifecycle_tasks_pending"] }),
    );
    f.details.mockResolvedValueOnce(success({ ...scheduleDetails, actions: [] }));
    await ready(f);
    await f.controller.save("Blocked");
    expect(f.write).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    f.controller.deactivate();
  }
});
it("superseded schedule review failures cannot erase a newer authorized review", async () => {
  const f = fixture("schedule"),
    held = deferred<Result<typeof scheduleReview>>();
  f.review.mockReturnValueOnce(held.promise);
  f.controller.activate();
  const signal = f.review.mock.lastCall?.[2];
  await f.controller.refresh();
  expect(signal?.aborted).toBe(true);
  held.reject(new Error("Late private read"));
  await held.promise.catch(() => undefined);
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "reviewing",
    review: scheduleReview,
    failure: null,
  });
  f.controller.deactivate();
});
