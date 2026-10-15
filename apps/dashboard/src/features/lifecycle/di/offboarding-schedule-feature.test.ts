import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import {
  canChangeOffboardingSchedule,
  canScheduleOffboarding,
} from "../domain/policies/offboarding-schedule-policy";
import { createLifecycleFeature } from "./lifecycle-feature";
import {
  scheduleAccess as access,
  scheduleCaseId as caseId,
  scheduleCompany as company,
  scheduleId as id,
  scheduleJobId as jobId,
  scheduleOperation as operation,
  schedulePermissions as permissions,
  scheduleReviewWire as reviewWire,
  scheduleDetails,
  scheduleReview,
  stoppedSchedule,
  scheduleWire as wire,
} from "./offboarding-schedule-fixture";

const signal = () => new AbortController().signal;
const creation = {
  id,
  caseId,
  expectedVersion: 3,
  employmentVersion: 7,
  basisRevision: reviewWire.basisRevision,
  reason: "Departure reviewed",
};
const change = {
  id,
  caseId,
  jobId,
  expectedVersion: 0,
  expectedJobVersion: 0,
  reason: "Plan changed",
};
const base = `/api/v1/companies/${company}/lifecycle/cases/${caseId}/offboarding-schedules`;
describe("scheduled offboarding HTTP and domain boundary", () => {
  it("requires all read grants and validates identifiers before acquiring schedules", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    for (const missing of permissions) {
      const scope = { companyId: company, permissions: permissions.filter((p) => p !== missing) };
      for (const result of [
        await feature.loadOffboardingSchedules.execute(scope, caseId, signal()),
        await feature.loadOffboardingScheduleReview.execute(scope, caseId, signal()),
        await feature.loadOffboardingSchedule.execute(scope, caseId, id, signal()),
      ])
        expect(result).toMatchObject({
          ok: false,
          failure: { code: "offboarding_access_required" },
        });
    }
    expect(
      await feature.loadOffboardingSchedule.execute(access, caseId, "../other", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadOffboardingSchedules.execute(access, "../other", signal()),
    ).toMatchObject({ ok: false });
    expect(
      await feature.loadOffboardingScheduleReview.execute(access, "../other", signal()),
    ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
    expect(canScheduleOffboarding(access, scheduleReview)).toBe(true);
    expect(
      canScheduleOffboarding(access, {
        ...scheduleReview,
        blockers: ["offboarding_schedule_active"],
      }),
    ).toBe(false);
    expect(
      canScheduleOffboarding({ ...access, companyId: id as string as CompanyId }, scheduleReview),
    ).toBe(false);
    expect(canChangeOffboardingSchedule(access, scheduleDetails, "cancel")).toBe(true);
    expect(canChangeOffboardingSchedule(access, scheduleDetails, "recover")).toBe(false);
  });
  it("maps scoped immutable review and history, strips transport-only data and accepts initial version zero", async () => {
    const input = structuredClone(wire),
      request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    request.mockResolvedValueOnce({ ...reviewWire, private: "PRIVATE" });
    expect(
      await feature.loadOffboardingScheduleReview.execute(access, caseId.toUpperCase(), signal()),
    ).toEqual({ ok: true, value: scheduleReview });
    expect(request.mock.lastCall?.[0]).toEqual({ path: `${base}/review` });
    request.mockResolvedValueOnce({ items: [{ ...input, private: "PRIVATE" }], nextCursor: null });
    const history = await feature.loadOffboardingSchedules.execute(access, caseId, signal());
    expect(history).toEqual({ ok: true, value: [scheduleDetails] });
    if (!history.ok) throw new Error("Expected history");
    input.job.version = 99;
    input.actions.length = 0;
    expect(history.value[0]?.job.version).toBe(0);
    expect(Object.isFrozen(history.value)).toBe(true);
    expect(Object.isFrozen(history.value[0]?.job)).toBe(true);
    expect(Object.isFrozen(history.value[0]?.actions)).toBe(true);
    expect(JSON.stringify(history)).not.toContain("PRIVATE");
    request.mockResolvedValueOnce({ id: id.toUpperCase(), version: 0 });
    expect(
      await feature.scheduleOffboarding.execute(
        access,
        operation,
        { ...creation, reason: " Departure reviewed " },
        signal(),
      ),
    ).toEqual({ ok: true, value: { id, version: 0 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: base,
      method: "POST",
      operationId: operation,
      body: {
        id,
        expectedVersion: 3,
        employmentVersion: 7,
        basisRevision: reviewWire.basisRevision,
        reason: creation.reason,
      },
    });
  });
  it("distinguishes cancellation job receipts from schedule recovery receipts", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    request.mockResolvedValueOnce({ id: jobId, version: 1 });
    expect(
      await feature.cancelOffboardingSchedule.execute(access, operation, change, signal()),
    ).toEqual({ ok: true, value: { id: jobId, version: 1 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `${base}/${id}/cancel`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 0, expectedJobVersion: 0, reason: change.reason },
    });
    request.mockResolvedValueOnce({ id, version: 1 });
    expect(
      await feature.recoverOffboardingSchedule.execute(
        access,
        operation,
        { ...change, expectedJobVersion: 4 },
        signal(),
      ),
    ).toEqual({ ok: true, value: { id, version: 1 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `${base}/${id}/recover`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 0, expectedJobVersion: 4, reason: change.reason },
    });
    for (const receipt of [
      { id, version: 1 },
      { id: jobId, version: 2 },
      { id: jobId, version: 0 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.cancelOffboardingSchedule.execute(access, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    for (const receipt of [
      { id: jobId, version: 1 },
      { id, version: 0 },
      { id, version: 2 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.recoverOffboardingSchedule.execute(access, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    for (const receipt of [
      { id, version: 1 },
      { id: jobId, version: 0 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.scheduleOffboarding.execute(access, operation, creation, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("allows an unclosed header with a stopped job and terminal cancellation history", async () => {
    const stopped = stoppedSchedule(),
      request = vi.fn<HttpClient["request"]>().mockResolvedValue(stopped),
      feature = createLifecycleFeature({ request });
    expect(
      await feature.loadOffboardingSchedule.execute(access, caseId, id.toUpperCase(), signal()),
    ).toMatchObject({
      ok: true,
      value: { status: "SCHEDULED", actions: ["recover"], job: { status: "FAILED" } },
    });
    const terminal = {
      ...stopped,
      status: "FAILED",
      version: 1,
      failureCode: stopped.job.failureCode,
      finishedAt: "2026-10-01T02:00:00Z",
      actions: [],
    };
    request.mockResolvedValueOnce(terminal);
    expect(
      await feature.loadOffboardingSchedule.execute(access, caseId, id, signal()),
    ).toMatchObject({ ok: true, value: { status: "FAILED" } });
    request.mockResolvedValueOnce({
      ...terminal,
      status: "CANCELLED",
      failureCode: "job_cancelled",
      job: {
        ...terminal.job,
        status: "CANCELLED",
        failureCode: "job_cancelled",
        cancellationRequested: true,
      },
    });
    expect(
      await feature.loadOffboardingSchedule.execute(access, caseId, id, signal()),
    ).toMatchObject({ ok: true, value: { status: "CANCELLED" } });
  });
  it("rejects cross-case, incoherent terminal/action states, shifted windows and invalid zones", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      action = createLifecycleFeature({ request }).loadOffboardingSchedule;
    for (const value of [
      { ...wire, id: jobId },
      { ...wire, caseId: jobId },
      { ...wire, appliedEmploymentRevision: 8 },
      { ...wire, status: "COMPLETED" },
      {
        ...wire,
        job: { ...wire.job, status: "SUCCEEDED", finishedAt: wire.scheduledFor, completedItems: 1 },
      },
      { ...wire, actions: ["recover"] },
      { ...wire, job: { ...wire.job, cancellationRequested: true } },
      {
        ...wire,
        status: "FAILED",
        version: 1,
        finishedAt: wire.scheduledFor,
        failureCode: "failed",
        actions: [],
      },
      { ...wire, lastWorkingDate: "2026-02-30" },
      { ...wire, scheduledFor: "2026-09-30T18:00:00Z", validUntil: "2026-10-07T18:00:00Z" },
      { ...wire, validUntil: "2026-10-06T17:00:00Z" },
      { ...wire, companyTimezone: "Invalid/Zone" },
      { ...wire, reason: " " },
      { ...wire, createdAt: wire.scheduledFor },
      { ...wire, job: { ...wire.job, version: Number.MAX_SAFE_INTEGER + 1 } },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(
        await action.execute(access, caseId, id, signal()),
        JSON.stringify(value),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("bounds ordered attempt history and rejects duplicate or discontinuous attempts", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      action = createLifecycleFeature({ request }).loadOffboardingSchedules;
    for (const payload of [
      { items: [wire], nextCursor: id },
      { items: [wire, wire], nextCursor: null },
      { items: [{ ...wire, attempt: 2 }], nextCursor: null },
      { items: Array.from({ length: 9 }, () => wire), nextCursor: null },
      {
        items: [wire, { ...wire, id: jobId, attempt: 2, job: { ...wire.job, id } }],
        nextCursor: null,
      },
    ]) {
      request.mockResolvedValueOnce(payload);
      expect(await action.execute(access, caseId, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("accepts DST-adjusted company midnight with exactly 168 hours of execution authority", async () => {
    const value = {
      ...reviewWire,
      case: { ...reviewWire.case, targetDate: "2026-03-08" },
      companyTimezone: "America/New_York",
      today: "2026-03-08",
      scheduledFor: "2026-03-09T04:00:00Z",
      validUntil: "2026-03-16T04:00:00Z",
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(value);
    expect(
      await createLifecycleFeature({ request }).loadOffboardingScheduleReview.execute(
        access,
        caseId,
        signal(),
      ),
    ).toMatchObject({ ok: true });
  });
  it.each([
    ["2026-10-31", "2026-11-01T05:00:00Z", "2026-11-08T05:00:00Z"],
    ["2026-03-07", "2026-03-08T05:00:00Z", "2026-03-15T05:00:00Z"],
  ])("accepts reviewed midnight after %s across overlap and gap", async (lastDay, start, end) => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    request.mockResolvedValueOnce({
      ...reviewWire,
      case: { ...reviewWire.case, targetDate: lastDay },
      companyTimezone: "America/Havana",
      today: lastDay,
      scheduledFor: start,
      validUntil: end,
    });
    expect(
      await feature.loadOffboardingScheduleReview.execute(access, caseId, signal()),
    ).toMatchObject({ ok: true });
    request.mockResolvedValueOnce({
      ...wire,
      companyTimezone: "America/Havana",
      lastWorkingDate: lastDay,
      createdAt: `${lastDay}T12:00:00Z`,
      scheduledFor: start,
      validUntil: end,
    });
    expect(
      await feature.loadOffboardingSchedule.execute(access, caseId, id, signal()),
    ).toMatchObject({ ok: true });
  });
  it("rejects the earlier occurrence of a reviewed repeated midnight", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...reviewWire,
      case: { ...reviewWire.case, targetDate: "2026-10-31" },
      companyTimezone: "America/Havana",
      today: "2026-10-31",
      scheduledFor: "2026-11-01T04:00:00Z",
      validUntil: "2026-11-08T04:00:00Z",
    });
    expect(
      await createLifecycleFeature({ request }).loadOffboardingScheduleReview.execute(
        access,
        caseId,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("rejects malformed review sources and opaque revisions", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      action = createLifecycleFeature({ request }).loadOffboardingScheduleReview;
    for (const value of [
      { ...reviewWire, case: { ...reviewWire.case, id: jobId } },
      { ...reviewWire, case: { ...reviewWire.case, status: "COMPLETED" } },
      { ...reviewWire, case: { ...reviewWire.case, kind: "ONBOARDING" } },
      { ...reviewWire, today: "2026-02-30" },
      { ...reviewWire, blockers: ["PRIVATE <server>"] },
      { ...reviewWire, blockers: ["changed", "changed"] },
      { ...reviewWire, basisRevision: "a" },
      { ...reviewWire, attempts: 9 },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(await action.execute(access, caseId, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("bounds command input, requires independent grants and never sends invalid I/O", async () => {
    const request = vi.fn<HttpClient["request"]>(),
      feature = createLifecycleFeature({ request });
    for (const input of [
      { ...creation, reason: " " },
      { ...creation, reason: "x".repeat(1001) },
      { ...creation, basisRevision: "" },
      { ...creation, expectedVersion: -1 },
      { ...creation, employmentVersion: Number.MAX_SAFE_INTEGER },
    ])
      expect(
        await feature.scheduleOffboarding.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_offboarding_schedule" } });
    for (const action of [feature.cancelOffboardingSchedule, feature.recoverOffboardingSchedule]) {
      for (const input of [
        { ...change, expectedVersion: 1 },
        { ...change, expectedJobVersion: Number.MAX_SAFE_INTEGER },
        { ...change, reason: " " },
      ])
        expect(await action.execute(access, operation, input, signal())).toMatchObject({
          ok: false,
          failure: { code: "invalid_offboarding_schedule" },
        });
      expect(
        await action.execute(access, "invalid" as OperationId, change, signal()),
      ).toMatchObject({ ok: false });
    }
    for (const missing of permissions.filter((p) => p !== "people.lifecycle.read")) {
      const scope = { companyId: company, permissions: permissions.filter((p) => p !== missing) };
      expect(
        await feature.scheduleOffboarding.execute(scope, operation, creation, signal()),
      ).toMatchObject({ ok: false, failure: { code: "offboarding_access_required" } });
      expect(
        await feature.cancelOffboardingSchedule.execute(scope, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "offboarding_access_required" } });
      expect(
        await feature.recoverOffboardingSchedule.execute(scope, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "offboarding_access_required" } });
    }
    expect(request).not.toHaveBeenCalled();
  });
  it("preserves policy failure classification, removes raw diagnostics and propagates cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(409, {
        code: "offboarding_schedule_changed",
        fields: {},
        parameters: {},
        detail: "PRIVATE",
      }),
    );
    const feature = createLifecycleFeature({ request });
    const result = await feature.scheduleOffboarding.execute(access, operation, creation, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "offboarding_schedule_changed" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const pending = new AbortController();
    pending.abort();
    expect(() =>
      feature.loadOffboardingSchedules.execute(access, caseId, pending.signal),
    ).toThrow();
    expect(() =>
      feature.scheduleOffboarding.execute(access, operation, creation, pending.signal),
    ).toThrow();
    request.mockRejectedValue(new DOMException("Cancelled", "AbortError"));
    await expect(
      feature.loadOffboardingSchedule.execute(access, caseId, id, signal()),
    ).rejects.toMatchObject({ name: "AbortError" });
    await expect(
      feature.cancelOffboardingSchedule.execute(access, operation, change, signal()),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
