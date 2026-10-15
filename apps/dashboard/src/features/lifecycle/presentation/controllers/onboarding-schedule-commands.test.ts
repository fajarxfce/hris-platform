import { describe, expect, it, vi } from "vitest";
import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import {
  onboardingAccess as access,
  deferred,
  onboardingDetails as details,
  onboardingEmployeeDetails as employee,
  onboardingEmployee as employeeId,
  onboardingJobId as jobId,
  onboardingAccount,
  onboardingReview as review,
  onboardingId as scheduleId,
} from "../../di/onboarding-schedule-fixture";
import type { LifecycleAssignee } from "../../domain/entities/lifecycle-assignee";
import type {
  OnboardingScheduleChange,
  OnboardingScheduleCreation,
} from "../../domain/entities/onboarding-schedule-command";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { OnboardingScheduleChangeController } from "./onboarding-schedule-change-controller";
import { OnboardingScheduleStartController } from "./onboarding-schedule-start-controller";

type Kind = "schedule" | "cancel" | "recover";
const member: LifecycleAssignee = {
  id: onboardingAccount,
  companyId: access.companyId,
  displayName: "Joining coordinator",
};
function fixture(kind: Kind, permissions = access.permissions) {
  const currentAccess = { ...access, permissions };
  const loadEmployee = vi.fn<LoadEmployee["execute"]>().mockResolvedValue(success(employee));
  const loadReview = vi
    .fn<LifecycleUseCases["loadOnboardingScheduleReview"]["execute"]>()
    .mockResolvedValue(success(review));
  const loadDetails = vi
    .fn<LifecycleUseCases["loadOnboardingSchedule"]["execute"]>()
    .mockResolvedValue(
      success({ ...details, actions: [kind === "recover" ? "recover" : "cancel"] }),
    );
  const write = vi
    .fn<
      (
        access: CompanyAccess,
        operation: OperationId,
        input: OnboardingScheduleCreation | OnboardingScheduleChange,
        signal: AbortSignal,
      ) => Promise<Result<MutationReceipt>>
    >()
    .mockImplementation(async (_access, _operation, input) =>
      success({ id: kind === "cancel" ? jobId : input.id, version: kind === "schedule" ? 0 : 1 }),
    );
  let sequence = 0;
  const next = vi.fn(() => `90000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const start =
    kind === "schedule"
      ? new OnboardingScheduleStartController(
          { execute: loadEmployee },
          {
            loadOnboardingScheduleReview: { execute: loadReview },
            scheduleOnboarding: { execute: write },
          },
          currentAccess,
          employeeId,
          "2026-10-02",
          next,
        )
      : null;
  const controller =
    start ??
    new OnboardingScheduleChangeController(
      { execute: loadDetails },
      { execute: write },
      kind === "recover" ? "recover" : "cancel",
      currentAccess,
      employeeId,
      scheduleId,
      next,
    );
  const save = (
    reason: string,
    assignees: Readonly<Record<string, LifecycleAssignee>> = { equipment: member },
  ) =>
    start
      ? start.save({ reason, assignees })
      : (controller as OnboardingScheduleChangeController).save(reason);
  const prepare = async () => {
    if (start) {
      await vi.waitFor(() => expect(start.getSnapshot().stage).toBe("editing"));
      await start.prepare(review.template, review.startsOn);
    }
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("reviewing"));
  };
  const ready = async () => {
    controller.activate();
    await prepare();
  };
  return {
    controller,
    start,
    ready,
    prepare,
    save,
    loadEmployee,
    loadReview,
    loadDetails,
    write,
    next,
  };
}
describe.each(["schedule", "cancel", "recover"] as const)("%s onboarding ownership", (kind) => {
  it("pins the reviewed basis, copies selections and permits one pending command", async () => {
    const f = fixture(kind);
    await f.ready();
    const held = deferred<Result<MutationReceipt>>();
    f.write.mockReturnValueOnce(held.promise);
    const assignees: Record<string, LifecycleAssignee> = { equipment: { ...member } };
    const pending = f.save(" Reviewed joining ", assignees);
    delete assignees.equipment;
    await f.save("Second command");
    await f.controller.retry();
    await f.controller.refresh();
    f.start?.revise();
    expect(f.write).toHaveBeenCalledOnce();
    const input = f.write.mock.lastCall?.[2];
    expect(Object.isFrozen(input)).toBe(true);
    expect(input).toMatchObject(
      kind === "schedule"
        ? {
            employmentId: employeeId,
            employmentVersion: 2,
            templateId: review.template.id,
            templateVersion: 3,
            targetDate: review.targetDate,
            startsOn: review.startsOn,
            basisRevision: review.basisRevision,
            assignees: { equipment: onboardingAccount },
            reason: "Reviewed joining",
          }
        : {
            id: scheduleId,
            employmentId: employeeId,
            jobId,
            expectedVersion: 0,
            expectedJobVersion: 0,
            reason: "Reviewed joining",
          },
    );
    if (input && "assignees" in input) {
      expect(Object.isFrozen(input.assignees)).toBe(true);
      expect(input.caseId).not.toBe(input.id);
    }
    held.resolve(
      success({
        id: kind === "cancel" ? jobId : (input?.id ?? ""),
        version: kind === "schedule" ? 0 : 1,
      }),
    );
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      operationId: null,
      scheduleId: input?.id,
    });
    await f.save("Duplicate");
    expect(f.write).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledTimes(kind === "schedule" ? 3 : 1);
    f.controller.deactivate();
  });
  it("retains an uncertain original through MFA and later conflicts while erasing private review", async () => {
    const f = fixture(kind);
    await f.ready();
    f.write
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("onboarding_schedule_changed"))
      .mockResolvedValueOnce(failed("onboarding_schedule_finished"));
    await f.save("Joining plan");
    const original = f.write.mock.lastCall;
    for (const code of [
      "mfa_required",
      "onboarding_schedule_changed",
      "onboarding_schedule_finished",
    ]) {
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        review: null,
        operationId: original?.[1],
        failure: { code },
      });
      if (f.start) expect(f.start.getSnapshot().employee).toBeNull();
      await f.controller.refresh();
      await f.save("Replacement");
      f.start?.revise();
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.write).toHaveBeenCalledTimes(5);
    for (const call of f.write.mock.calls) {
      expect(call[1]).toBe(original?.[1]);
      expect(call[2]).toBe(original?.[2]);
    }
    expect(f.loadReview.mock.calls.length + f.loadDetails.mock.calls.length).toBe(1);
    expect(f.next).toHaveBeenCalledTimes(kind === "schedule" ? 3 : 1);
    f.controller.deactivate();
  });
  it.each(["onboarding_schedule_changed", "stale_version", "onboarding_already_completed"])(
    "a first known %s rejection requires a new review",
    async (code) => {
      const f = fixture(kind);
      await f.ready();
      f.write.mockResolvedValueOnce(failed(code));
      await f.save("Original review");
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "conflict",
        operationId: null,
        failure: { code },
      });
      await f.save("Unreviewed");
      expect(f.write).toHaveBeenCalledOnce();
      f.loadReview.mockResolvedValueOnce(
        success({
          ...review,
          employmentVersion: 3,
          template: { ...review.template, version: 4 },
          basisRevision: "b".repeat(64),
        }),
      );
      f.loadDetails.mockResolvedValueOnce(
        success({
          ...details,
          job: { ...details.job, version: 2 },
          actions: [kind === "recover" ? "recover" : "cancel"],
        }),
      );
      await f.controller.refresh();
      await f.prepare();
      await f.save("Updated review");
      expect(f.write.mock.lastCall?.[2]).toMatchObject(
        kind === "schedule"
          ? { employmentVersion: 3, templateVersion: 4, basisRevision: "b".repeat(64) }
          : { expectedJobVersion: 2 },
      );
      expect(f.write.mock.calls[1]?.[1]).not.toBe(f.write.mock.calls[0]?.[1]);
      f.controller.deactivate();
    },
  );
  it("clears a newly denied review and requires explicit acquisition", async () => {
    const f = fixture(kind);
    await f.ready();
    f.write.mockResolvedValueOnce(failed("onboarding_access_required"));
    await f.save("Private reason");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    if (f.start) expect(f.start.getSnapshot().employee).toBeNull();
    await f.save("Again");
    await f.controller.retry();
    expect(f.write).toHaveBeenCalledOnce();
    await f.controller.refresh();
    await f.prepare();
    f.controller.deactivate();
  });
  it.each(["success", "failure"])(
    "disposal rejects late %s after reactivation",
    async (outcome) => {
      const f = fixture(kind);
      await f.ready();
      const held = deferred<Result<MutationReceipt>>();
      f.write.mockReturnValueOnce(held.promise);
      const pending = f.save("Joining plan"),
        signal = f.write.mock.lastCall?.[3],
        listener = vi.fn(),
        stop = f.controller.subscribe(listener);
      stop();
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      await f.ready();
      if (outcome === "success") held.resolve(success({ id: scheduleId, version: 0 }));
      else held.reject(new Error("PRIVATE late exception"));
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
  it("sanitizes unexpected write failures and keeps the original identity", async () => {
    const f = fixture(kind);
    await f.ready();
    f.write.mockRejectedValueOnce(new Error("PRIVATE"));
    await f.save("Reviewed");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
});
it("blocked reviews and unavailable actions allocate no command", async () => {
  for (const kind of ["schedule", "cancel", "recover"] as const) {
    const f = fixture(kind);
    f.loadReview.mockResolvedValueOnce(
      success({ ...review, blockers: ["onboarding_already_completed"] }),
    );
    f.loadDetails.mockResolvedValueOnce(success({ ...details, actions: [] }));
    await f.ready();
    await f.save("Blocked");
    expect(f.write).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    f.controller.deactivate();
  }
});
it("checks schedule discovery access before acquiring employee data", async () => {
  const f = fixture("schedule", []);
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
  expect(f.loadEmployee).not.toHaveBeenCalled();
  expect(f.loadReview).not.toHaveBeenCalled();
  f.controller.deactivate();
});
it.each(["success", "failure"])("superseded preparation ignores a late %s", async (outcome) => {
  const f = fixture("schedule");
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  const held = deferred<Result<typeof review>>();
  f.loadReview.mockReturnValueOnce(held.promise);
  const pending = f.start?.prepare(review.template, review.startsOn),
    signal = f.loadReview.mock.lastCall?.[3];
  await f.controller.refresh();
  expect(signal?.aborted).toBe(true);
  await f.prepare();
  if (outcome === "success") held.resolve(success({ ...review, basisRevision: "c".repeat(64) }));
  else held.reject(new Error("PRIVATE obsolete review"));
  await pending;
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", review, failure: null });
  f.controller.deactivate();
});
it("a denied preparation clears the employee while ordinary validation retains the selection", async () => {
  const f = fixture("schedule");
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  f.loadReview.mockResolvedValueOnce(failed("invalid_onboarding_schedule"));
  await f.start?.prepare(review.template, "bad-date");
  expect(f.start?.getSnapshot()).toMatchObject({ stage: "editing", employee, review: null });
  f.loadReview.mockResolvedValueOnce(failed("employee_not_found"));
  await f.start?.prepare(review.template, review.startsOn);
  expect(f.start?.getSnapshot()).toMatchObject({
    stage: "unavailable",
    employee: null,
    review: null,
  });
  f.controller.deactivate();
});
it("replaced employee acquisition cannot restore obsolete data", async () => {
  const f = fixture("schedule"),
    held = deferred<Result<typeof employee>>();
  f.loadEmployee.mockReturnValueOnce(held.promise);
  f.controller.activate();
  const signal = f.loadEmployee.mock.lastCall?.[3];
  await f.controller.refresh();
  expect(signal?.aborted).toBe(true);
  held.reject(new Error("PRIVATE"));
  await held.promise.catch(() => undefined);
  expect(f.start?.getSnapshot()).toMatchObject({ stage: "editing", employee, failure: null });
  f.controller.deactivate();
});
it("identifier generation failure submits nothing and permits correction", async () => {
  const f = fixture("schedule");
  await f.ready();
  f.next.mockImplementationOnce(() => {
    throw new Error("PRIVATE random failure");
  });
  await f.save("Reviewed");
  expect(f.write).not.toHaveBeenCalled();
  expect(f.start?.getSnapshot()).toMatchObject({
    stage: "reviewing",
    operationId: null,
    failure: { code: "unexpected_error" },
  });
  await f.save("Reviewed");
  expect(f.controller.getSnapshot().stage).toBe("saved");
  f.controller.deactivate();
});
