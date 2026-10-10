import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { EmployeeId } from "../../people/domain/entities/employee";
import type { LifecycleCase, LifecycleCaseId } from "../domain/entities/lifecycle-case";
import type { LifecycleCaseChange } from "../domain/entities/lifecycle-case-change";
import type { LifecycleTemplateId } from "../domain/entities/lifecycle-template";
import { lifecycleCaseActions } from "../domain/policies/lifecycle-case-change-policy";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const id = "a0000000-abcd-4000-8000-000000000001" as LifecycleCaseId;
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["people.lifecycle.manage"] };
const change: LifecycleCaseChange = {
  caseId: id,
  expectedVersion: 2,
  reason: "Checklist reviewed",
};
const signal = () => new AbortController().signal;
const record: LifecycleCase = {
  id,
  companyId,
  employee: {
    id: "40000000-0000-4000-8000-000000000001" as EmployeeId,
    employeeNumber: "E-001",
    name: "Employee",
  },
  kind: "ONBOARDING",
  status: "OPEN",
  version: 2,
  targetDate: "2026-10-01",
  templateId: "90000000-0000-4000-8000-000000000001" as LifecycleTemplateId,
  templateVersion: 0,
  templateName: "Onboarding",
  createdBy: account,
  createdAt: "2026-10-01T00:00:00Z",
  tasks: [
    {
      key: "equipment",
      title: "Equipment",
      required: true,
      status: "DONE",
      dueDate: "2026-10-01",
      assigneeId: account,
      completedBy: account,
      completedAt: "2026-10-01T01:00:00Z",
    },
    {
      key: "welcome",
      title: "Welcome session",
      required: false,
      status: "WAIVED",
      dueDate: "2026-10-01",
      assigneeId: null,
      completedBy: account,
      completedAt: "2026-10-01T01:00:00Z",
    },
  ],
};

describe("lifecycle case change boundary", () => {
  it("allows cancellation of open cases but requires all required and optional tasks to resolve before onboarding completion", () => {
    expect(lifecycleCaseActions(access, record)).toEqual({
      cancel: true,
      completeOnboarding: true,
    });
    expect(lifecycleCaseActions(access, { ...record, kind: "OFFBOARDING" })).toEqual({
      cancel: true,
      completeOnboarding: false,
    });
    for (const tasks of [
      record.tasks.map((task) => (task.required ? { ...task, status: "WAIVED" as const } : task)),
      record.tasks.map((task) => (!task.required ? { ...task, status: "PENDING" as const } : task)),
      record.tasks.map((task) => ({ ...task, status: "PENDING" as const })),
    ])
      expect(lifecycleCaseActions(access, { ...record, tasks })).toEqual({
        cancel: true,
        completeOnboarding: false,
      });
    for (const invalid of [
      { ...record, companyId: "10000000-0000-4000-8000-000000000002" as CompanyId },
      { ...record, status: "COMPLETED" as const },
      { ...record, status: "CANCELLED" as const },
      { ...record, version: Number.MAX_SAFE_INTEGER },
    ])
      expect(lifecycleCaseActions(access, invalid)).toEqual({
        cancel: false,
        completeOnboarding: false,
      });
    for (const permissions of [
      [],
      ["people.manage"],
      ["people.lifecycle.read"],
      ["people.lifecycle.perform"],
    ])
      expect(lifecycleCaseActions({ companyId, permissions }, record)).toEqual({
        cancel: false,
        completeOnboarding: false,
      });
  });

  for (const [method, endpoint] of [
    ["cancelCase", "cancel"],
    ["completeOnboarding", "complete-onboarding"],
  ] as const) {
    it(`${method} checks authority and command bounds before I/O`, async () => {
      const request = vi.fn<HttpClient["request"]>();
      const action = createLifecycleFeature({ request })[method];
      for (const permissions of [
        [],
        ["people.manage"],
        ["people.lifecycle.read"],
        ["people.lifecycle.perform"],
      ])
        expect(
          await action.execute({ companyId, permissions }, operation, change, signal()),
        ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      for (const invalid of [
        { ...change, caseId: "../case" },
        { ...change, expectedVersion: -1 },
        { ...change, expectedVersion: Number.MAX_SAFE_INTEGER },
        { ...change, expectedVersion: 0.5 },
        { ...change, reason: " " },
        { ...change, reason: "x".repeat(1001) },
      ])
        expect(await action.execute(access, operation, invalid, signal())).toMatchObject({
          ok: false,
          failure: { code: "invalid_lifecycle_change" },
        });
      expect(
        await action.execute(access, "invalid" as OperationId, change, signal()),
      ).toMatchObject({ ok: false });
      expect(request).not.toHaveBeenCalled();
    });
    it(`${method} targets its own endpoint and accepts only the matching next aggregate version`, async () => {
      const request = vi
        .fn<HttpClient["request"]>()
        .mockResolvedValue({ id: id.toUpperCase(), version: 3, private: "PRIVATE" });
      const action = createLifecycleFeature({ request })[method];
      const result = await action.execute(
        access,
        operation,
        { ...change, caseId: id.toUpperCase(), reason: " Checklist reviewed " },
        signal(),
      );
      expect(request.mock.lastCall?.[0]).toEqual({
        path: `/api/v1/companies/${companyId}/lifecycle/cases/${id}/${endpoint}`,
        method: "POST",
        operationId: operation,
        body: { expectedVersion: 2, reason: "Checklist reviewed" },
      });
      expect(result).toEqual({ ok: true, value: { id, version: 3 } });
      for (const receipt of [
        { id: account, version: 3 },
        { id, version: 2 },
        { id, version: 4 },
      ]) {
        request.mockResolvedValueOnce(receipt);
        expect(await action.execute(access, operation, change, signal())).toMatchObject({
          ok: false,
          failure: { code: "invalid_response" },
        });
      }
    });
    it(`${method} preserves a definite rejection without retrying or leaking technical text`, async () => {
      const request = vi
        .fn<HttpClient["request"]>()
        .mockRejectedValue(
          new HttpResponseError(409, { code: "lifecycle_case_not_open", detail: "PRIVATE" }),
        );
      const result = await createLifecycleFeature({ request })[method].execute(
        access,
        operation,
        change,
        signal(),
      );
      expect(result).toMatchObject({ ok: false, failure: { code: "lifecycle_case_not_open" } });
      expect(JSON.stringify(result)).not.toContain("PRIVATE");
      expect(request).toHaveBeenCalledOnce();
    });
    it(`${method} propagates cancellation during a pending request`, async () => {
      let resolve!: (value: unknown) => void;
      const request = vi.fn<HttpClient["request"]>().mockImplementation(
        () =>
          new Promise((done) => {
            resolve = done;
          }),
      );
      const action = createLifecycleFeature({ request })[method];
      const owner = new AbortController();
      const pending = action.execute(access, operation, change, owner.signal);
      owner.abort();
      resolve({ id, version: 3 });
      await expect(pending).rejects.toMatchObject({ name: "AbortError" });
      expect(() => action.execute(access, operation, change, owner.signal)).toThrow();
      expect(request).toHaveBeenCalledOnce();
    });
  }
});
