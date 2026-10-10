import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentDetails } from "../../domain/entities/employment-details";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmploymentEditableFields,
  EmploymentEditorController,
} from "./employment-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const employeeId = "40000000-0000-4000-8000-000000000001" as EmployeeId;
const details: EmploymentDetails = {
  asOf: "2026-10-01",
  employee: {
    id: employeeId,
    companyId,
    employeeNumber: "EMP-001",
    legalName: "Employee One",
    email: null,
    version: 7,
    appliedRevision: 0,
    terms: {
      startDate: "2026-01-01",
      effectiveFrom: "2026-01-01",
      endDate: null,
      contract: "PERMANENT",
      status: "ACTIVE",
      branchId: null,
      departmentId: null,
      positionId: null,
      costCenterId: null,
      managerId: null,
    },
  },
  branch: null,
  department: null,
  position: null,
  costCenter: null,
  manager: null,
};
const fields: EmploymentEditableFields = {
  terms: {
    effectiveFrom: "2027-01-01",
    endDate: null,
    contract: "PERMANENT",
    status: "SUSPENDED",
    branchId: null,
    departmentId: null,
    positionId: null,
    costCenterId: null,
    managerId: null,
  },
  reason: "Approved employment change",
};
const owners: EmploymentEditorController[] = [];
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
});
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture(permissions = ["people.read", "people.manage"]) {
  const loadEmploymentDetails = {
    execute: vi
      .fn<PeopleUseCases["loadEmploymentDetails"]["execute"]>()
      .mockResolvedValue(success(details)),
  };
  const reviseEmployment = {
    execute: vi
      .fn<PeopleUseCases["reviseEmployment"]["execute"]>()
      .mockResolvedValue(success({ id: employeeId, version: 8 })),
  };
  let issued = 0;
  const next = vi.fn(() => `60000000-0000-4000-8000-${String(++issued).padStart(12, "0")}`);
  const controller = new EmploymentEditorController(
    { loadEmploymentDetails, reviseEmployment },
    { companyId, permissions },
    employeeId,
    details.asOf,
    next,
  );
  owners.push(controller);
  controller.activate();
  return { controller, loadEmploymentDetails, reviseEmployment, next };
}

describe("employment editor ownership", () => {
  it("pins the aggregate version and original start date, freezes submitted terms and excludes overlapping saves or reads", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = pending<Result<MutationReceipt>>();
    f.reviseEmployment.execute.mockReturnValueOnce(held.promise);
    const input = { terms: { ...fields.terms, startDate: "2020-01-01" }, reason: fields.reason };
    const saving = f.controller.save(input);
    input.reason = "Changed after submission";
    input.terms.effectiveFrom = "2028-01-01";
    await f.controller.save(fields);
    await f.controller.refresh();
    expect(f.reviseEmployment.execute).toHaveBeenCalledOnce();
    const submitted = f.reviseEmployment.execute.mock.lastCall?.[2];
    expect(submitted).toEqual({
      employeeId,
      expectedVersion: 7,
      terms: { ...fields.terms, startDate: "2026-01-01" },
      reason: fields.reason,
    });
    expect(Object.isFrozen(submitted)).toBe(true);
    expect(Object.isFrozen(submitted?.terms)).toBe(true);
    held.resolve(success({ id: employeeId, version: 8 }));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: employeeId, version: 8 },
      savedDate: "2027-01-01",
      operationId: null,
    });
    expect(f.loadEmploymentDetails.execute).toHaveBeenCalledOnce();
  });

  it("retains the original operation and payload after an unknown result even when MFA or a later conflict rejects replay", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.reviseEmployment.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    const first = f.reviseEmployment.execute.mock.calls[0];
    await f.controller.refresh();
    await f.controller.save({ ...fields, reason: "Replacement" });
    for (let attempt = 0; attempt < 2; attempt += 1) {
      await f.controller.retrySave();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      expect(f.reviseEmployment.execute.mock.lastCall?.[1]).toBe(first?.[1]);
      expect(f.reviseEmployment.execute.mock.lastCall?.[2]).toBe(first?.[2]);
    }
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.loadEmploymentDetails.execute).toHaveBeenCalledOnce();
  });

  it("requires explicit reload after a definite conflict and uses the newly observed aggregate version", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.reviseEmployment.execute.mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields);
    expect(f.reviseEmployment.execute).toHaveBeenCalledOnce();
    f.loadEmploymentDetails.execute.mockResolvedValueOnce(
      success({ ...details, employee: { ...details.employee, version: 9 } }),
    );
    await f.controller.refresh();
    await f.controller.save(fields);
    expect(f.reviseEmployment.execute.mock.lastCall?.[2].expectedVersion).toBe(9);
    expect(f.next).toHaveBeenCalledTimes(2);
  });

  it("allows correction of a definite validation rejection without discarding the loaded assignments", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.reviseEmployment.execute.mockResolvedValueOnce(failed("organization_assignment_unavailable"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      details,
      operationId: null,
      failure: { code: "organization_assignment_unavailable" },
    });
    await f.controller.save({ ...fields, reason: "Corrected assignment" });
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
  });

  it("does not acquire employment details when editing is not authorized", async () => {
    for (const permissions of [
      ["people.read"],
      ["people.manage"],
      ["people.team.read", "people.manage"],
    ]) {
      const f = fixture(permissions);
      await f.controller.save(fields);
      expect(f.controller.getSnapshot().stage).toBe("unavailable");
      expect(f.loadEmploymentDetails.execute).not.toHaveBeenCalled();
      expect(f.reviseEmployment.execute).not.toHaveBeenCalled();
    }
  });

  it.each([false, true])(
    "disposal cancels a pending save and ignores its late completion after reactivation (failure: %s)",
    async (rejects) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      const held = pending<Result<MutationReceipt>>();
      f.reviseEmployment.execute.mockReturnValueOnce(held.promise);
      const saving = f.controller.save(fields);
      const signal = f.reviseEmployment.execute.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      expect(f.controller.getSnapshot().details).toBeNull();
      f.controller.activate();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
      if (rejects) held.reject(new Error("PRIVATE LATE FAILURE"));
      else held.resolve(success({ id: employeeId, version: 8 }));
      await saving;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );

  it("cancels superseded detail reads and cannot restore their obsolete versions", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const held = pending<Result<EmploymentDetails>>();
    f.loadEmploymentDetails.execute.mockReturnValueOnce(held.promise);
    const old = f.controller.refresh();
    const signal = f.loadEmploymentDetails.execute.mock.lastCall?.[3];
    expect(f.controller.getSnapshot().details).toBeNull();
    f.loadEmploymentDetails.execute.mockResolvedValueOnce(
      success({ ...details, employee: { ...details.employee, version: 9 } }),
    );
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.resolve(success(details));
    await old;
    expect(f.controller.getSnapshot().details?.employee.version).toBe(9);
    expect(f.reviseEmployment.execute).not.toHaveBeenCalled();
  });
});
