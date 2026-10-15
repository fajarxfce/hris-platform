import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import {
  canChangeOnboardingSchedule,
  canReadOnboardingSchedules,
  canScheduleOnboarding,
} from "../domain/policies/onboarding-schedule-policy";
import { createLifecycleFeature } from "./lifecycle-feature";
import {
  onboardingAccess as access,
  onboardingAccount as account,
  onboardingCaseId as caseId,
  onboardingCompany as company,
  deferred,
  onboardingDetails as details,
  onboardingEmployee as employee,
  onboardingId as id,
  onboardingJobId as jobId,
  onboardingOperation as operation,
  onboardingPermissions as permissions,
  onboardingReview as review,
  onboardingReviewWire as reviewWire,
  onboardingSelection as selection,
  stoppedOnboarding,
  onboardingWire as wire,
} from "./onboarding-schedule-fixture";

const signal = () => new AbortController().signal;
const base = `/api/v1/companies/${company}/employees/${employee}/onboarding-schedules`;
const creation = {
  ...selection,
  id,
  caseId,
  employmentId: employee,
  employmentVersion: 2,
  templateVersion: 3,
  basisRevision: reviewWire.basisRevision,
  assignees: wire.assignees as Readonly<Record<string, AccountId>>,
  reason: "Reviewed joining checklist",
};
const change = {
  id,
  employmentId: employee,
  jobId,
  expectedVersion: 0,
  expectedJobVersion: 0,
  reason: "Joining postponed",
};
describe("onboarding schedule transport and policy boundary", () => {
  it("requires separate read grants and validates parent identifiers before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    for (const missing of permissions) {
      const scope = { ...access, permissions: permissions.filter((p) => p !== missing) };
      for (const result of [
        await feature.loadOnboardingSchedules.execute(scope, employee, signal()),
        await feature.loadOnboardingSchedule.execute(scope, employee, id, signal()),
        await feature.loadOnboardingScheduleReview.execute(scope, employee, selection, signal()),
      ])
        expect(result).toMatchObject({
          ok: false,
          failure: { code: "onboarding_access_required" },
        });
    }
    expect(
      await feature.loadOnboardingSchedules.execute(access, "../other", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadOnboardingSchedule.execute(access, employee, "../other", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadOnboardingScheduleReview.execute(
        access,
        employee,
        { ...selection, startsOn: "2026-02-30" },
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
    expect(canReadOnboardingSchedules(permissions)).toBe(true);
    expect(canScheduleOnboarding(access, review)).toBe(true);
    expect(
      canScheduleOnboarding(access, { ...review, blockers: ["onboarding_already_completed"] }),
    ).toBe(false);
    expect(canScheduleOnboarding({ ...access, companyId: id as string as CompanyId }, review)).toBe(
      false,
    );
    expect(canChangeOnboardingSchedule(access, details, "cancel")).toBe(true);
    expect(canChangeOnboardingSchedule(access, details, "recover")).toBe(false);
  });
  it("maps exact employee/template/date selections, strips private fields and freezes retained evidence", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request }),
      payload = structuredClone(wire);
    request.mockResolvedValueOnce({
      ...reviewWire,
      private: "PRIVATE",
      employee: { ...reviewWire.employee, name: "N".repeat(200), email: "PRIVATE" },
    });
    const result = await feature.loadOnboardingScheduleReview.execute(
      access,
      employee.toUpperCase(),
      { ...selection, extra: "PRIVATE" } as typeof selection,
      signal(),
    );
    expect(result).toMatchObject({ ok: true, value: { employee: { name: "N".repeat(200) } } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `${base}/review?${new URLSearchParams(selection)}`,
    });
    request.mockResolvedValueOnce({
      items: [{ ...payload, private: "PRIVATE" }],
      nextCursor: null,
    });
    const history = await feature.loadOnboardingSchedules.execute(access, employee, signal());
    expect(history).toEqual({ ok: true, value: [details] });
    if (!history.ok) throw new Error("Expected retained schedule");
    const task = payload.template.tasks[0];
    if (!task) throw new Error("Expected checklist task");
    task.title = "Changed";
    payload.assignees.equipment = jobId;
    expect(history.value[0]?.template.tasks[0]?.title).toBe("Prepare equipment");
    expect(history.value[0]?.assignees.equipment).toBe(account);
    expect(Object.isFrozen(history.value[0]?.template.tasks)).toBe(true);
    expect(Object.isFrozen(history.value[0]?.assignees)).toBe(true);
  });
  it("sends only reviewed command fields and preserves separate schedule, case and job identities", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    request.mockResolvedValueOnce({ id: id.toUpperCase(), version: 0 });
    expect(
      await feature.scheduleOnboarding.execute(
        { companyId: company, permissions: ["people.lifecycle.manage"] },
        operation,
        { ...creation, reason: " Reviewed joining checklist " },
        signal(),
      ),
    ).toEqual({ ok: true, value: { id, version: 0 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: base,
      method: "POST",
      operationId: operation,
      body: {
        ...selection,
        id,
        caseId,
        employmentVersion: 2,
        templateVersion: 3,
        basisRevision: review.basisRevision,
        assignees: creation.assignees,
        reason: creation.reason,
      },
    });
    request.mockResolvedValueOnce({ id: jobId, version: 1 });
    expect(
      await feature.cancelOnboardingSchedule.execute(access, operation, change, signal()),
    ).toEqual({ ok: true, value: { id: jobId, version: 1 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `${base}/${id}/cancel`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 0, expectedJobVersion: 0, reason: change.reason },
    });
    request.mockResolvedValueOnce({ id, version: 1 });
    expect(
      await feature.recoverOnboardingSchedule.execute(
        access,
        operation,
        { ...change, expectedJobVersion: 4 },
        signal(),
      ),
    ).toEqual({ ok: true, value: { id, version: 1 } });
    expect(request.mock.lastCall?.[0]).toMatchObject({
      path: `${base}/${id}/recover`,
      body: { expectedVersion: 0, expectedJobVersion: 4, reason: change.reason },
    });
  });
  it("copies caller-owned assignees before a pending request", async () => {
    const wait = deferred<unknown>(),
      request = vi.fn<HttpClient["request"]>().mockReturnValue(wait.promise);
    const assignees = { equipment: account },
      input = { ...creation, assignees };
    const pending = createLifecycleFeature({ request }).scheduleOnboarding.execute(
      access,
      operation,
      input,
      signal(),
    );
    assignees.equipment = jobId as string as AccountId;
    const captured = request.mock.lastCall?.[0].body;
    expect(captured).toMatchObject({ assignees: { equipment: account } });
    if (!captured) throw new Error("Expected captured command");
    expect(Object.isFrozen((captured as { assignees: unknown }).assignees)).toBe(true);
    wait.resolve({ id, version: 0 });
    expect(await pending).toMatchObject({ ok: true });
  });
  it("rejects unrelated or advanced mutation receipts", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    for (const [run, wrong] of [
      [
        () => feature.scheduleOnboarding.execute(access, operation, creation, signal()),
        { id: caseId, version: 0 },
      ],
      [
        () => feature.scheduleOnboarding.execute(access, operation, creation, signal()),
        { id, version: 1 },
      ],
      [
        () => feature.cancelOnboardingSchedule.execute(access, operation, change, signal()),
        { id, version: 1 },
      ],
      [
        () => feature.cancelOnboardingSchedule.execute(access, operation, change, signal()),
        { id: jobId, version: 2 },
      ],
      [
        () => feature.recoverOnboardingSchedule.execute(access, operation, change, signal()),
        { id: jobId, version: 1 },
      ],
      [
        () => feature.recoverOnboardingSchedule.execute(access, operation, change, signal()),
        { id, version: 0 },
      ],
    ] as const) {
      request.mockResolvedValueOnce(wrong);
      expect(await run()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("accepts pending terminal jobs for explicit recovery and coherent started/closed outcomes", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request }),
      stopped = stoppedOnboarding();
    for (const value of [
      stopped,
      {
        ...stopped,
        status: "FAILED",
        version: 1,
        finishedAt: stopped.job.finishedAt,
        failureCode: stopped.job.failureCode,
        actions: [],
      },
      {
        ...wire,
        status: "STARTED",
        version: 1,
        finishedAt: "2026-10-01T00:00:00Z",
        actions: [],
        job: {
          ...wire.job,
          status: "SUCCEEDED",
          version: 2,
          attempts: 1,
          completedItems: 1,
          finishedAt: "2026-10-01T00:00:00Z",
        },
      },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(
        await feature.loadOnboardingSchedule.execute(access, employee, id, signal()),
      ).toMatchObject({ ok: true });
    }
  });
  it("rejects cross-employee data, invalid assignments and incoherent job outcomes", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      action = createLifecycleFeature({ request }).loadOnboardingSchedule;
    for (const value of [
      { ...wire, id: jobId },
      { ...wire, employmentId: jobId },
      { ...wire, appliedEmploymentRevision: 3 },
      { ...wire, template: { ...wire.template, kind: "OFFBOARDING" } },
      { ...wire, assignees: { unknown: account } },
      { ...wire, version: 1 },
      { ...wire, status: "STARTED" },
      { ...wire, actions: ["recover"] },
      { ...wire, job: { ...wire.job, cancellationRequested: true } },
      { ...wire, scheduledFor: "2026-09-30T18:00:00Z" },
      { ...wire, validUntil: "2026-10-03T17:00:00Z" },
      { ...wire, companyTimezone: "Invalid/Zone" },
      { ...wire, reason: " " },
      { ...wire, createdAt: wire.scheduledFor },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(
        await action.execute(access, employee, id, signal()),
        JSON.stringify(value),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("bounds retained attempts and refuses duplicate planned cases", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      action = createLifecycleFeature({ request }).loadOnboardingSchedules;
    for (const value of [
      { items: [wire], nextCursor: id },
      { items: Array.from({ length: 9 }, () => wire), nextCursor: null },
      { items: [{ ...wire, attempt: 2 }], nextCursor: null },
      { items: [wire, wire], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(await action.execute(access, employee, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it.each([
    ["2026-11-02", "2026-11-01", "2026-11-01T05:00:00Z", "2026-11-03T05:00:00Z"],
    ["2026-10-31", "2026-10-30", "2026-10-30T04:00:00Z", "2026-11-01T05:00:00Z"],
    ["2026-03-09", "2026-03-08", "2026-03-08T05:00:00Z", "2026-03-10T04:00:00Z"],
  ])(
    "retains company-midnight boundaries for %s / %s",
    async (targetDate, startsOn, scheduledFor, validUntil) => {
      const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
        ...reviewWire,
        targetDate,
        startsOn,
        scheduledFor,
        validUntil,
        companyTimezone: "America/Havana",
      });
      expect(
        await createLifecycleFeature({ request }).loadOnboardingScheduleReview.execute(
          access,
          employee,
          { ...selection, targetDate, startsOn },
          signal(),
        ),
      ).toMatchObject({ ok: true });
    },
  );
  it("rejects mismatched review selection but preserves blocked reviews for explanation", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      action = createLifecycleFeature({ request }).loadOnboardingScheduleReview;
    request.mockResolvedValueOnce({ ...reviewWire, blockers: ["onboarding_already_completed"] });
    expect(await action.execute(access, employee, selection, signal())).toMatchObject({
      ok: true,
      value: { blockers: ["onboarding_already_completed"] },
    });
    for (const value of [
      { ...reviewWire, employee: { ...reviewWire.employee, id: jobId } },
      { ...reviewWire, template: { ...reviewWire.template, id: jobId } },
      { ...reviewWire, targetDate: "2026-10-03" },
      { ...reviewWire, startsOn: "2026-10-02" },
      { ...reviewWire, blockers: ["x", "x"] },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(await action.execute(access, employee, selection, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("rejects malformed commands and excessive assignments before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    for (const input of [
      { ...creation, reason: " " },
      { ...creation, reason: "x".repeat(1001) },
      { ...creation, startsOn: "2026-10-03" },
      { ...creation, basisRevision: "invalid" },
      { ...creation, employmentVersion: Number.MAX_SAFE_INTEGER },
      {
        ...creation,
        assignees: Object.fromEntries(Array.from({ length: 65 }, (_, i) => [`task${i}`, account])),
      },
    ])
      expect(
        await feature.scheduleOnboarding.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_onboarding_schedule" } });
    for (const action of [feature.cancelOnboardingSchedule, feature.recoverOnboardingSchedule]) {
      expect(
        await action.execute(access, operation, { ...change, expectedVersion: 1 }, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await action.execute(access, "invalid" as OperationId, change, signal()),
      ).toMatchObject({ ok: false });
      expect(
        await action.execute({ ...access, permissions: [] }, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "onboarding_access_required" } });
    }
    expect(request).not.toHaveBeenCalled();
  });
  it("preserves safe failure classification and propagates cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(409, {
        code: "onboarding_schedule_changed",
        fields: {},
        parameters: {},
        detail: "PRIVATE",
      }),
    );
    const feature = createLifecycleFeature({ request }),
      result = await feature.scheduleOnboarding.execute(access, operation, creation, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "onboarding_schedule_changed" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const pending = new AbortController();
    pending.abort();
    expect(() =>
      feature.loadOnboardingScheduleReview.execute(access, employee, selection, pending.signal),
    ).toThrow();
    const running = new AbortController(),
      wait = deferred<unknown>();
    request.mockReturnValueOnce(wait.promise);
    const cancelled = feature.loadOnboardingSchedule.execute(access, employee, id, running.signal);
    running.abort();
    wait.resolve(wire);
    await expect(cancelled).rejects.toBe(running.signal.reason);
  });
});
