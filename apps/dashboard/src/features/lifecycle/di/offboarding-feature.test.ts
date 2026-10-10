import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LifecycleCaseDto } from "../data/models/lifecycle-case-dto";
import type { OffboardingCompletion } from "../domain/entities/offboarding-completion";
import {
  canReviewOffboardingCase,
  validateOffboardingReview,
} from "../domain/policies/offboarding-policy";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "a0000000-abcd-4000-8000-000000000001";
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const permissions = [
  "people.lifecycle.read",
  "people.lifecycle.manage",
  "people.manage",
  "people.offboard",
];
const access = { companyId, permissions };
const record: LifecycleCaseDto = {
  id,
  employmentId: "40000000-0000-4000-8000-000000000001",
  employee: {
    id: "40000000-0000-4000-8000-000000000001",
    name: "Employee",
    employeeNumber: "E-001",
  },
  kind: "OFFBOARDING",
  status: "OPEN",
  version: 3,
  targetDate: "2026-09-30",
  templateId: "90000000-0000-4000-8000-000000000001",
  templateVersion: 0,
  templateName: "Departure",
  createdBy: "20000000-0000-4000-8000-000000000001",
  createdAt: "2026-09-29T00:00:00Z",
  tasks: [
    {
      key: "equipment",
      title: "Equipment",
      required: true,
      status: "DONE",
      dueDate: "2026-09-30",
      assigneeId: null,
      completedBy: "20000000-0000-4000-8000-000000000001",
      completedAt: "2026-09-30T01:00:00Z",
    },
  ],
};
const wire = { case: record, employmentVersion: 7, today: "2026-10-01" };
const command: OffboardingCompletion = {
  caseId: id,
  expectedVersion: 3,
  employmentVersion: 7,
  reason: "Departure reviewed",
};
const signal = () => new AbortController().signal;

describe("offboarding boundary", () => {
  it("requires all review grants and valid identifiers before acquiring data", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLifecycleFeature({ request });
    for (const missing of permissions) {
      const scope = { companyId, permissions: permissions.filter((p) => p !== missing) };
      expect(await feature.loadOffboardingReview.execute(scope, id, signal())).toMatchObject({
        ok: false,
        failure: { code: "offboarding_access_required" },
      });
    }
    expect(await feature.loadOffboardingReview.execute(access, "../case", signal())).toMatchObject({
      ok: false,
      failure: { code: "lifecycle_case_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
  });
  it("maps a scoped immutable review with company date and both observed versions", async () => {
    const input = structuredClone(wire);
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ ...input, private: "PRIVATE" });
    const result = await createLifecycleFeature({ request }).loadOffboardingReview.execute(
      access,
      id.toUpperCase(),
      signal(),
    );
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/lifecycle/cases/${id}/offboarding-review`,
    });
    expect(result).toMatchObject({
      ok: true,
      value: { today: "2026-10-01", employmentVersion: 7, case: { id, companyId, version: 3 } },
    });
    if (!result.ok) throw new Error("Expected review");
    const task = input.case.tasks[0];
    if (!task) throw new Error("Expected equipment task");
    task.title = "Changed externally";
    expect(result.value.case.tasks[0]?.title).toBe("Equipment");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.case.tasks)).toBe(true);
    expect(Object.keys(result.value).sort()).toEqual(["case", "employmentVersion", "today"]);
    expect(canReviewOffboardingCase(access, result.value.case)).toBe(true);
    expect(
      canReviewOffboardingCase({ ...access, companyId: id as CompanyId }, result.value.case),
    ).toBe(false);
    expect(validateOffboardingReview(result.value)).toMatchObject({ ok: true });
    expect(validateOffboardingReview({ ...result.value, today: "2026-09-30" })).toMatchObject({
      ok: false,
      failure: { code: "offboarding_date_not_reached" },
    });
    expect(
      validateOffboardingReview({
        ...result.value,
        case: {
          ...result.value.case,
          tasks: result.value.case.tasks.map((task) => ({ ...task, status: "PENDING" })),
        },
      }),
    ).toMatchObject({ ok: false, failure: { code: "required_lifecycle_tasks_pending" } });
    expect(
      validateOffboardingReview({
        ...result.value,
        case: {
          ...result.value.case,
          tasks: result.value.case.tasks.map((task) => ({
            ...task,
            required: false,
            status: "PENDING",
          })),
        },
      }),
    ).toMatchObject({ ok: false, failure: { code: "lifecycle_tasks_unresolved" } });
  });
  it("rejects unrelated, closed, wrong-kind, malformed-date and unsafe-version reviews inside the boundary", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const action = createLifecycleFeature({ request }).loadOffboardingReview;
    for (const value of [
      { ...wire, case: { ...record, id: operation } },
      { ...wire, case: { ...record, status: "CANCELLED" } },
      { ...wire, case: { ...record, kind: "ONBOARDING" } },
      { ...wire, today: "2026-02-30" },
      { ...wire, employmentVersion: -1 },
      { ...wire, employmentVersion: Number.MAX_SAFE_INTEGER + 1 },
      { ...wire, case: { ...record, tasks: [record.tasks[0], record.tasks[0]] } },
    ]) {
      request.mockResolvedValueOnce(value);
      expect(await action.execute(access, id, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("bounds and normalizes both versions and preserves the exact completion receipt", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id.toUpperCase(), version: 4 });
    const action = createLifecycleFeature({ request }).completeOffboarding;
    for (const invalid of [
      { ...command, employmentVersion: -1 },
      { ...command, employmentVersion: 0.5 },
      { ...command, employmentVersion: Number.MAX_SAFE_INTEGER },
      { ...command, expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...command, reason: " " },
      { ...command, reason: "x".repeat(1001) },
      { ...command, caseId: "../case" },
    ])
      expect(await action.execute(access, operation, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_offboarding" },
      });
    for (const missing of permissions.filter((p) => p !== "people.lifecycle.read"))
      expect(
        await action.execute(
          { companyId, permissions: permissions.filter((p) => p !== missing) },
          operation,
          command,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "offboarding_access_required" } });
    expect(await action.execute(access, "invalid" as OperationId, command, signal())).toMatchObject(
      { ok: false },
    );
    expect(request).not.toHaveBeenCalled();
    expect(
      await action.execute(
        access,
        operation,
        { ...command, caseId: id.toUpperCase(), reason: " Departure reviewed " },
        signal(),
      ),
    ).toEqual({ ok: true, value: { id, version: 4 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/lifecycle/cases/${id}/complete-offboarding`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 3, employmentVersion: 7, reason: "Departure reviewed" },
    });
    for (const receipt of [
      { id: operation, version: 4 },
      { id, version: 3 },
      { id, version: 5 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await action.execute(access, operation, command, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it("preserves stable policy failures, sanitizes technical text, and propagates cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(409, {
        code: "stale_employment_version",
        fields: {},
        parameters: {},
        detail: "PRIVATE",
      }),
    );
    const feature = createLifecycleFeature({ request });
    const result = await feature.completeOffboarding.execute(access, operation, command, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "stale_employment_version" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadOffboardingReview.execute(access, id, cancelled.signal)).toThrow();
    expect(() =>
      feature.completeOffboarding.execute(access, operation, command, cancelled.signal),
    ).toThrow();
    request.mockRejectedValue(new DOMException("Cancelled", "AbortError"));
    await expect(feature.loadOffboardingReview.execute(access, id, signal())).rejects.toMatchObject(
      { name: "AbortError" },
    );
    await expect(
      feature.completeOffboarding.execute(access, operation, command, signal()),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
