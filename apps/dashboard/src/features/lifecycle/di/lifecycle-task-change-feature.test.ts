import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../domain/entities/lifecycle-case";
import type { LifecycleTaskChange } from "../domain/entities/lifecycle-task-change";
import type { LifecycleTaskContext } from "../domain/entities/lifecycle-task-context";
import { availableLifecycleTaskStatuses } from "../domain/policies/lifecycle-task-change-policy";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const account = "20000000-0000-4000-8000-000000000001" as AccountId;
const id = "a0000000-abcd-4000-8000-000000000001" as LifecycleCaseId;
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const performer = { companyId, permissions: ["people.lifecycle.perform"] };
const manager = { companyId, permissions: ["people.lifecycle.manage"] };
const change: LifecycleTaskChange = {
  caseId: id,
  taskKey: "equipment",
  expectedVersion: 2,
  status: "DONE",
  reason: "Equipment received",
};
const signal = () => new AbortController().signal;
const context: LifecycleTaskContext = {
  companyId,
  caseId: id,
  caseVersion: 2,
  caseStatus: "OPEN",
  kind: "ONBOARDING",
  employee: {
    id: "b0000000-0000-4000-8000-000000000001" as LifecycleTaskContext["employee"]["id"],
    employeeNumber: "E01",
    name: "Employee",
  },
  task: {
    key: "equipment",
    title: "Review equipment",
    required: true,
    dueDate: "2026-10-01",
    assigneeId: account,
    status: "PENDING",
    completedBy: null,
    completedAt: null,
  },
};

describe("lifecycle task change boundary", () => {
  it("offers only transitions allowed for the observed company, assignment, requirement and case state", () => {
    expect(availableLifecycleTaskStatuses(performer, account, context)).toEqual(["DONE"]);
    expect(availableLifecycleTaskStatuses(manager, account, context)).toEqual(["DONE"]);
    const optional = { ...context, task: { ...context.task, required: false } };
    expect(availableLifecycleTaskStatuses(manager, account, optional)).toEqual(["DONE", "WAIVED"]);
    expect(availableLifecycleTaskStatuses(performer, account, optional)).toEqual(["DONE"]);
    expect(
      availableLifecycleTaskStatuses(manager, account, {
        ...optional,
        task: { ...optional.task, status: "DONE" },
      }),
    ).toEqual(["PENDING", "WAIVED"]);
    for (const permissions of [[], ["people.lifecycle.read"], ["people.manage"]])
      expect(availableLifecycleTaskStatuses({ companyId, permissions }, account, context)).toEqual(
        [],
      );
    expect(
      availableLifecycleTaskStatuses(performer, account, {
        ...context,
        task: { ...context.task, assigneeId: null },
      }),
    ).toEqual([]);
    for (const invalid of [
      { ...context, companyId: "10000000-0000-4000-8000-000000000002" as CompanyId },
      { ...context, caseStatus: "COMPLETED" as const },
      { ...context, caseStatus: "CANCELLED" as const },
      { ...context, caseVersion: Number.MAX_SAFE_INTEGER },
    ])
      expect(availableLifecycleTaskStatuses(manager, account, invalid)).toEqual([]);
  });

  it("checks permissions, command bounds and waiver authority before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const action = createLifecycleFeature({ request }).changeTask;
    for (const permissions of [[], ["people.lifecycle.read"], ["people.manage"]])
      expect(
        await action.execute({ companyId, permissions }, operation, change, signal()),
      ).toMatchObject({ ok: false, failure: { code: "lifecycle_access_required" } });
    for (const invalid of [
      { ...change, caseId: "../foreign" },
      { ...change, taskKey: "../other" },
      { ...change, taskKey: "UPPER" },
      { ...change, expectedVersion: -1 },
      { ...change, expectedVersion: 1.5 },
      { ...change, expectedVersion: Number.MAX_SAFE_INTEGER },
      { ...change, reason: " " },
      { ...change, reason: "x".repeat(1001) },
    ])
      expect(await action.execute(manager, operation, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_lifecycle_change" },
      });
    expect(
      await action.execute(performer, operation, { ...change, status: "WAIVED" }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "lifecycle_task_cannot_be_waived" } });
    expect(await action.execute(manager, "invalid" as OperationId, change, signal())).toMatchObject(
      { ok: false },
    );
    expect(request).not.toHaveBeenCalled();
  });

  it("writes only normalized command fields with the observed version and stable operation", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id.toUpperCase(), version: 3, private: "PRIVATE" });
    const owner = signal();
    const input = {
      ...change,
      caseId: id.toUpperCase(),
      taskKey: " equipment ",
      reason: " Equipment received ",
      employeeName: "PRIVATE",
      expectedAssignee: "FORGED",
    };
    const result = await createLifecycleFeature({ request }).changeTask.execute(
      performer,
      operation,
      input,
      owner,
    );
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path: `/api/v1/companies/${companyId}/lifecycle/cases/${id}/tasks/equipment`,
        method: "PUT",
        operationId: operation,
        body: { expectedVersion: 2, status: "DONE", reason: "Equipment received" },
      },
      owner,
    );
    expect(result).toEqual({ ok: true, value: { id, version: 3 } });
    if (!result.ok) throw new Error("Expected receipt");
    expect(Object.isFrozen(result.value)).toBe(true);
  });

  it("accepts only the matching next-version receipt", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const action = createLifecycleFeature({ request }).changeTask;
    for (const receipt of [
      { id: "a0000000-abcd-4000-8000-000000000002", version: 3 },
      { id, version: 2 },
      { id, version: 4 },
      { id, version: Number.MAX_SAFE_INTEGER + 1 },
      null,
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await action.execute(manager, operation, change, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({ id, version: Number.MAX_SAFE_INTEGER });
    expect(
      await action.execute(
        manager,
        operation,
        { ...change, expectedVersion: Number.MAX_SAFE_INTEGER - 1, status: "WAIVED" },
        signal(),
      ),
    ).toMatchObject({ ok: true });
  });

  it("keeps server failure classifications without leaking diagnostics or retrying", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(409, {
          code: "stale_version",
          fields: {},
          parameters: {},
          detail: "PRIVATE",
        }),
      )
      .mockRejectedValueOnce(new TypeError("PRIVATE"));
    const action = createLifecycleFeature({ request }).changeTask;
    expect(await action.execute(manager, operation, change, signal())).toMatchObject({
      ok: false,
      failure: { code: "stale_version" },
    });
    const result = await action.execute(manager, operation, change, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "connection_unavailable" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledTimes(2);
  });

  it("propagates cancellation before or during a write without accepting its late receipt", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const action = createLifecycleFeature({ request }).changeTask;
    const aborted = new AbortController();
    aborted.abort();
    expect(() => action.execute(manager, operation, change, aborted.signal)).toThrow();
    expect(request).not.toHaveBeenCalled();
    let resolve!: (value: unknown) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const owner = new AbortController();
    const pending = action.execute(manager, operation, change, owner.signal);
    owner.abort();
    resolve({ id, version: 3 });
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledOnce();
  });
});
