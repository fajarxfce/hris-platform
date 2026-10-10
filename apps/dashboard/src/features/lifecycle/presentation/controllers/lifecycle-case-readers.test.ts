import { describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type {
  LifecycleCase,
  LifecycleCaseId,
  LifecycleCasePage,
} from "../../domain/entities/lifecycle-case";
import type { LifecycleHistoryPage } from "../../domain/entities/lifecycle-event";
import type {
  AssignedLifecycleTaskPage,
  LifecycleTaskContext,
} from "../../domain/entities/lifecycle-task-context";
import type { LifecycleTemplateId } from "../../domain/entities/lifecycle-template";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { lifecycleCaseParameters, lifecycleCaseSearch } from "../models/lifecycle-case-route";
import { AssignedLifecycleTasksController } from "./assigned-lifecycle-tasks-controller";
import { LifecycleCaseController } from "./lifecycle-case-controller";
import { LifecycleCasesController } from "./lifecycle-cases-controller";
import { LifecycleHistoryController } from "./lifecycle-history-controller";

const id = "a0000000-0000-4000-8000-000000000001" as LifecycleCaseId;
const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const accountId = "20000000-0000-4000-8000-000000000001" as AccountId;
const access = { companyId, permissions: ["people.lifecycle.read", "people.lifecycle.perform"] };
const record: LifecycleCase = Object.freeze({
  id,
  companyId,
  employee: Object.freeze({
    id: "b0000000-0000-4000-8000-000000000001" as EmployeeId,
    employeeNumber: "E01",
    name: "Example employee",
  }),
  kind: "ONBOARDING",
  status: "OPEN",
  targetDate: "2026-10-01",
  templateId: "c0000000-0000-4000-8000-000000000001" as LifecycleTemplateId,
  templateVersion: 0,
  templateName: "Standard onboarding",
  version: 2,
  createdBy: accountId,
  createdAt: "2026-10-01T00:00:00Z",
  tasks: Object.freeze([
    Object.freeze({
      key: "equipment",
      title: "Review equipment",
      required: true,
      dueDate: "2026-10-01",
      assigneeId: accountId,
      status: "PENDING",
      completedBy: null,
      completedAt: null,
    }),
  ]),
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("lifecycle read ownership", () => {
  it("pins a selected task to the observed case and discards superseded reads and scope failures", async () => {
    const first = deferred<Result<LifecycleCase>>();
    const execute = vi
      .fn<LifecycleUseCases["loadCase"]["execute"]>()
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce(success(record));
    const controller = new LifecycleCaseController({ execute }, access, id);
    controller.activate();
    controller.activate();
    expect(execute).toHaveBeenCalledTimes(1);
    const refresh = controller.refresh();
    expect(execute.mock.calls[0]?.[2].aborted).toBe(true);
    await refresh;
    first.resolve(failed("mfa_required"));
    await first.promise;
    expect(controller.getSnapshot()).toMatchObject({ stage: "ready", case: record, failure: null });
    controller.openTask("missing");
    expect(controller.getSnapshot().selectedTask).toBeNull();
    controller.openTask("equipment");
    const selected = controller.getSnapshot().selectedTask;
    expect(selected).toMatchObject({
      caseId: id,
      caseVersion: 2,
      caseStatus: "OPEN",
      employee: record.employee,
    });
    expect(Object.isFrozen(selected)).toBe(true);
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(2);
    controller.reportScopeFailure({ code: "connection_unavailable", fields: {}, parameters: {} });
    expect(controller.getSnapshot().selectedTask).toBe(selected);
    controller.reportScopeFailure({ code: "mfa_required", fields: {}, parameters: {} });
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      case: null,
      selectedTask: null,
      failure: { code: "mfa_required" },
    });
    controller.deactivate();
    controller.openTask("equipment");
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().case).toBeNull();
  });

  it("retains one case page, cancels previous work, and cannot republish a late result after disposal", async () => {
    const pending = deferred<Result<LifecycleCasePage>>();
    const execute = vi
      .fn<LifecycleUseCases["loadCases"]["execute"]>()
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(success({ items: [record], nextCursor: null }))
      .mockResolvedValueOnce(failed("access_denied"));
    const search = { status: "OPEN", employmentId: null, after: null };
    const controller = new LifecycleCasesController({ execute }, access, search);
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    await controller.refresh();
    expect(controller.getSnapshot().page?.items).toEqual([record]);
    expect(execute.mock.calls[0]?.[2].aborted).toBe(true);
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      page: null,
      failure: { code: "access_denied" },
    });
    unsubscribe();
    const notifications = listener.mock.calls.length;
    controller.deactivate();
    pending.resolve(success({ items: [record], nextCursor: null }));
    await pending.promise;
    expect(controller.getSnapshot().page).toBeNull();
    expect(listener).toHaveBeenCalledTimes(notifications);
    expect(execute).toHaveBeenCalledTimes(3);
  });

  it("uses a composite task identity and clears assignment context before accepting a new page", async () => {
    const task = record.tasks[0];
    if (!task) throw new Error("Expected task");
    const assignment: LifecycleTaskContext = {
      companyId,
      caseId: id,
      caseVersion: 2,
      caseStatus: "OPEN",
      employee: record.employee,
      kind: record.kind,
      task,
    };
    const first = deferred<Result<AssignedLifecycleTaskPage>>();
    const late = deferred<Result<AssignedLifecycleTaskPage>>();
    const execute = vi
      .fn<LifecycleUseCases["loadAssignedTasks"]["execute"]>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(late.promise);
    const controller = new AssignedLifecycleTasksController({ execute }, access, accountId, null);
    controller.activate();
    first.resolve(success({ items: [assignment], nextCursor: null }));
    await first.promise;
    controller.openTask("equipment");
    expect(controller.getSnapshot().selectedTask).toBeNull();
    controller.openTask(`${id}:equipment`);
    expect(controller.getSnapshot().selectedTask).toBe(assignment);
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(1);
    controller.closeTask();
    const refresh = controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      page: null,
      selectedTask: null,
    });
    controller.deactivate();
    expect(execute.mock.calls[1]?.[3].aborted).toBe(true);
    late.reject(new Error("Late private failure"));
    await refresh;
    expect(controller.getSnapshot()).toMatchObject({
      page: null,
      selectedTask: null,
      failure: null,
    });
  });

  it("history owns one cancellable page and selection without retaining a released listener", async () => {
    const first = deferred<Result<LifecycleHistoryPage>>();
    const late = deferred<Result<LifecycleHistoryPage>>();
    const execute = vi
      .fn<LifecycleUseCases["loadHistory"]["execute"]>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(late.promise);
    const controller = new LifecycleHistoryController({ execute }, access, id, "1");
    const listener = vi.fn();
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    controller.activate();
    first.resolve(
      success({
        items: [
          {
            version: 2,
            taskKey: "equipment",
            action: "TASK_PENDING",
            assigneeId: accountId,
            actorId: accountId,
            reason: "Task review",
            recordedAt: "2026-10-01T00:00:00Z",
          },
        ],
        nextCursor: null,
      }),
    );
    await first.promise;
    controller.open("1");
    expect(controller.getSnapshot().selected).toBeNull();
    controller.open("2");
    expect(controller.getSnapshot().selected?.reason).toBe("Task review");
    const refresh = controller.refresh();
    expect(controller.getSnapshot().selected).toBeNull();
    controller.deactivate();
    unsubscribe();
    const notifications = listener.mock.calls.length;
    late.resolve(failed("access_denied"));
    await refresh;
    expect(execute.mock.calls[1]?.[3].aborted).toBe(true);
    expect(controller.getSnapshot()).toMatchObject({ page: null, selected: null, failure: null });
    expect(listener).toHaveBeenCalledTimes(notifications);
  });

  it("keeps filters in the URL but discards them when the selected company changes", () => {
    const original = { status: "", employmentId: record.employee.id, after: id };
    const parameters = lifecycleCaseParameters(companyId, original);
    expect(lifecycleCaseSearch(parameters, companyId)).toEqual(original);
    expect(
      lifecycleCaseSearch(parameters, "10000000-0000-4000-8000-000000000002" as CompanyId),
    ).toEqual({ status: "OPEN", employmentId: null, after: null });
    expect(
      lifecycleCaseSearch(new URLSearchParams("status=invalid&after=broken"), companyId),
    ).toEqual({ status: "invalid", employmentId: null, after: "broken" });
  });
});
