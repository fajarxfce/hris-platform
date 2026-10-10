import { describe, expect, it } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeImportId } from "../../domain/entities/employee-import";
import type { EmployeeImportSummary } from "../../domain/entities/employee-import-summary";
import { EmployeeImportAttemptsController } from "./employee-import-attempts-controller";
import { EmployeeImportController } from "./employee-import-controller";
import { EmployeeImportRowsController } from "./employee-import-rows-controller";
import { EmployeeImportsController } from "./employee-imports-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "30000000-0000-4000-8000-000000000001" as EmployeeImportId;
const access = {
  companyId,
  permissions: ["people.import", "people.manage", "people.profile.read", "people.profile.manage"],
};
const page = Object.freeze({ items: Object.freeze([]), nextCursor: null });
const summary: EmployeeImportSummary = {
  jobStatus: "SUCCEEDED",
  cancellationRequested: false,
  availableActions: ["apply", "cancel"],
  batch: {
    id,
    companyId,
    fileName: "source.csv",
    rowCount: 1,
    status: "REVIEW",
    jobId: id,
    sourceHash: "a".repeat(64),
    version: 1,
    createdBy: "actor" as EmployeeImportSummary["batch"]["createdBy"],
    createdAt: "2026-10-01T00:00:00Z",
    reason: "Intake",
  },
  counts: { PENDING: 0, READY: 1, INVALID: 0, APPLIED: 0, REJECTED: 0 },
};
function deferredLoad<T>(value: T) {
  const calls: { signal: AbortSignal; complete: () => void; fail: () => void }[] = [];
  return {
    calls,
    execute: (...args: unknown[]): Promise<Result<T>> =>
      new Promise((resolve, reject) => {
        calls.push({
          signal: args.at(-1) as AbortSignal,
          complete: () => resolve(success(value)),
          fail: () => reject(new Error("late failure")),
        });
      }),
  };
}
const factories = [
  {
    name: "directory",
    create: () => {
      const load = deferredLoad(page);
      return { load, controller: new EmployeeImportsController(load, access, null) };
    },
  },
  {
    name: "summary",
    create: () => {
      const load = deferredLoad(summary);
      return { load, controller: new EmployeeImportController(load, access, id) };
    },
  },
  {
    name: "rows",
    create: () => {
      const load = deferredLoad(page);
      return { load, controller: new EmployeeImportRowsController(load, access, id, null) };
    },
  },
  {
    name: "attempts",
    create: () => {
      const load = deferredLoad(page);
      return { load, controller: new EmployeeImportAttemptsController(load, access, id, null) };
    },
  },
];
describe("owned employee import reads", () => {
  for (const factory of factories) {
    it(`${factory.name} discards superseded failures and bounds observation to its owner`, async () => {
      const { load, controller } = factory.create();
      let notifications = 0;
      const unsubscribe = controller.subscribe(() => {
        notifications += 1;
      });
      controller.activate();
      controller.activate();
      expect(load.calls).toHaveLength(1);
      const refreshed = controller.refresh();
      expect(load.calls[0]?.signal.aborted).toBe(true);
      load.calls[1]?.complete();
      await refreshed;
      expect(controller.getSnapshot().stage).toBe("ready");
      load.calls[0]?.fail();
      await Promise.resolve();
      await Promise.resolve();
      expect(controller.getSnapshot().stage).toBe("ready");
      unsubscribe();
      const observed = notifications;
      controller.deactivate();
      await controller.refresh();
      expect(notifications).toBe(observed);
      expect(load.calls).toHaveLength(2);
    });
    it(`${factory.name} clears data on disposal and ignores late success after reactivation`, async () => {
      const { load, controller } = factory.create();
      controller.activate();
      controller.deactivate();
      controller.activate();
      expect(load.calls[0]?.signal.aborted).toBe(true);
      load.calls[0]?.complete();
      await Promise.resolve();
      await Promise.resolve();
      expect(controller.getSnapshot().stage).toBe("loading");
      load.calls[1]?.complete();
      await Promise.resolve();
      await Promise.resolve();
      expect(controller.getSnapshot().stage).toBe("ready");
      controller.deactivate();
      expect(JSON.stringify(controller.getSnapshot())).not.toContain("source.csv");
    });
  }
  it("losing row access clears the retained summary and aborts its pending refresh", async () => {
    const load = deferredLoad(summary);
    const controller = new EmployeeImportController(load, access, id);
    controller.activate();
    load.calls[0]?.complete();
    await Promise.resolve();
    expect(controller.getSnapshot().summary).toBe(summary);
    const refreshed = controller.refresh();
    controller.reportScopeFailure({
      code: "employee_import_access_required",
      fields: {},
      parameters: {},
    });
    expect(load.calls[1]?.signal.aborted).toBe(true);
    load.calls[1]?.complete();
    await refreshed;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      summary: null,
      failure: { code: "employee_import_access_required" },
    });
    controller.deactivate();
  });
  it("refresh removes selected private proposals and definite failures do not retry", async () => {
    const record = {
      number: 1,
      employeeNumber: "EMP01",
      legalName: "Private draft",
      status: "INVALID" as const,
      issues: { startDate: "invalid_date" },
      proposed: null,
      createdEmploymentId: null,
    };
    let calls = 0;
    const load = {
      execute: async () =>
        ++calls === 1
          ? success({ items: [record], nextCursor: null })
          : failed("employee_import_access_required"),
    };
    const controller = new EmployeeImportRowsController(load, access, id, null);
    controller.activate();
    await Promise.resolve();
    controller.open("1");
    expect(controller.getSnapshot().selected?.legalName).toBe("Private draft");
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      selected: null,
      page: null,
      stage: "unavailable",
    });
    controller.open("1");
    expect(controller.getSnapshot().selected).toBeNull();
    expect(calls).toBe(2);
    controller.deactivate();
  });
});
