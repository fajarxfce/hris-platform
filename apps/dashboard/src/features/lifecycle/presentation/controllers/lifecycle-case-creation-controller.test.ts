import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { Employee, EmployeeId } from "../../../people/domain/entities/employee";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleCaseCreationController } from "./lifecycle-case-creation-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const permissions = ["people.read", "people.lifecycle.read", "people.lifecycle.manage"];
const employee: Employee = {
  id: "40000000-0000-4000-8000-000000000001" as EmployeeId,
  companyId,
  employeeNumber: "E-001",
  legalName: "Fixture employee",
  email: null,
  terms: {
    effectiveFrom: "2026-01-01",
    startDate: "2026-01-01",
    endDate: null,
    contract: "PERMANENT",
    status: "ACTIVE",
  },
  version: 2,
  appliedRevision: 1,
};
const template = (version = 2): LifecycleTemplate => ({
  id: "90000000-abcd-4000-8000-000000000001" as LifecycleTemplateId,
  companyId,
  code: "ONBOARD",
  name: "Onboarding",
  kind: "ONBOARDING",
  active: true,
  version,
  tasks: [{ key: "equipment", title: "Equipment", required: true, dueDays: 0 }],
});
const fields = () => ({
  template: template(),
  targetDate: "2026-10-01",
  reason: "Start onboarding",
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (value: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(grants = permissions) {
  const load = vi.fn<LoadEmployee["execute"]>().mockResolvedValue(success(employee));
  const save = vi
    .fn<LifecycleUseCases["startCase"]["execute"]>()
    .mockImplementation(async (_access, _operation, command) =>
      success({ id: command.id, version: 0 }),
    );
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LifecycleCaseCreationController(
    { execute: load },
    { execute: save },
    { companyId, permissions: grants },
    employee.id,
    "2026-10-01",
    next,
  );
  return { controller, load, save, next };
}

describe("lifecycle case creation ownership", () => {
  it("loads the scoped employee and captures one immutable case command despite repeated actions", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    expect(f.load.mock.lastCall?.slice(0, 3)).toEqual([
      { companyId, permissions },
      employee.id,
      "2026-10-01",
    ]);
    const held = deferred<Result<MutationReceipt>>();
    f.save.mockReturnValueOnce(held.promise);
    const proposed = { ...fields(), id: "ignored", employmentId: "ignored", templateVersion: 0 };
    const pending = f.controller.save(proposed);
    await f.controller.save(fields());
    await f.controller.refresh();
    await f.controller.retry();
    const captured = f.save.mock.lastCall?.[2];
    expect(captured).toEqual({
      id: f.controller.caseId,
      employmentId: employee.id,
      templateId: template().id,
      templateVersion: 2,
      targetDate: "2026-10-01",
      assignees: {},
      reason: "Start onboarding",
    });
    for (const value of [captured, captured?.assignees]) expect(Object.isFrozen(value)).toBe(true);
    proposed.template = template(9);
    proposed.targetDate = "2027-01-01";
    expect(captured?.templateVersion).toBe(2);
    expect(captured?.targetDate).toBe("2026-10-01");
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.save).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    held.resolve(success({ id: f.controller.caseId, version: 0 }));
    await pending;
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: f.controller.caseId, version: 0 },
      operationId: null,
    });
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it("retains an uncertain command through MFA and a later template rejection until recovery", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_template_version"));
    await f.controller.save(fields());
    const original = f.save.mock.lastCall;
    await f.controller.save({ ...fields(), reason: "Other request" });
    await f.controller.refresh();
    for (const code of ["mfa_required", "stale_template_version"]) {
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", failure: { code } });
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.save.mock.calls) {
      expect(call[1]).toBe(original?.[1]);
      expect(call[2]).toBe(original?.[2]);
    }
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.load).toHaveBeenCalledOnce();
    f.controller.deactivate();
  });
  it("accepts a reviewed replacement template only after a definite rejection", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.save.mockResolvedValueOnce(failed("stale_template_version"));
    await f.controller.save(fields());
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    await f.controller.retry();
    expect(f.save).toHaveBeenCalledOnce();
    await f.controller.save({ ...fields(), template: template(3) });
    expect(f.save.mock.lastCall?.[2].templateVersion).toBe(3);
    expect(f.save.mock.lastCall?.[2].id).toBe(f.controller.caseId);
    expect(f.save.mock.lastCall?.[1]).not.toBe(f.save.mock.calls[0]?.[1]);
    f.controller.deactivate();
  });
  it("does not start with an absent, inactive or foreign template", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    for (const selected of [
      null,
      { ...template(), active: false },
      { ...template(), companyId: "10000000-0000-4000-8000-000000000002" as CompanyId },
    ]) {
      await f.controller.save({ ...fields(), template: selected });
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        failure: { code: "lifecycle_template_unavailable" },
      });
    }
    expect(f.save).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("requires employee and template reads as well as management for this form", async () => {
    for (const grants of permissions.map((missing) => permissions.filter((p) => p !== missing))) {
      const f = fixture(grants);
      f.controller.activate();
      await f.controller.save(fields());
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        failure: { code: "access_denied" },
      });
      expect(f.load).not.toHaveBeenCalled();
      expect(f.save).not.toHaveBeenCalled();
      f.controller.deactivate();
    }
  });
  it.each(["success", "failure"] as const)(
    "aborts a disposed write and ignores late %s",
    async (outcome) => {
      const f = fixture();
      f.controller.activate();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      const held = deferred<Result<MutationReceipt>>();
      f.save.mockReturnValueOnce(held.promise);
      const pending = f.controller.save(fields());
      const signal = f.save.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success({ id: f.controller.caseId, version: 0 }));
      else held.reject(new Error("PRIVATE LATE FAILURE"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "loading",
        employee: null,
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );
  it("drops a superseded employee read and supports an explicit retry without retaining listeners", async () => {
    const f = fixture();
    const held = deferred<Result<Employee>>();
    f.load.mockReturnValueOnce(held.promise).mockResolvedValueOnce(failed("employee_not_found"));
    const observer = vi.fn();
    const remove = f.controller.subscribe(observer);
    f.controller.activate();
    const signal = f.load.mock.lastCall?.[3];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(employee));
    await held.promise;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", employee: null });
    await f.controller.save(fields());
    expect(f.save).not.toHaveBeenCalled();
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", employee });
    remove();
    observer.mockClear();
    f.controller.deactivate();
    expect(observer).not.toHaveBeenCalled();
  });
});
