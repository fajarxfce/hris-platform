import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  EmployeeCreationController,
  type EmployeeCreationFields,
} from "./employee-creation-controller";

const fields: EmployeeCreationFields = {
  employeeNumber: "EMP-001",
  legalName: "New employee",
  birthDate: null,
  nationality: "ID",
  email: null,
  startDate: "2027-01-01",
  endDate: null,
  contract: "PERMANENT",
  status: "ACTIVE",
  branchId: null,
  departmentId: null,
  positionId: null,
  costCenterId: null,
  managerId: null,
  reason: "Onboarding",
};
const owners: EmployeeCreationController[] = [];
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
});
function fixture(permissions = ["people.manage"]) {
  let issued = 0;
  const next = vi.fn(() => `60000000-0000-4000-8000-${String(++issued).padStart(12, "0")}`);
  const create = { execute: vi.fn<PeopleUseCases["createEmployee"]["execute"]>() };
  const controller = new EmployeeCreationController(
    create,
    { companyId: "10000000-0000-4000-8000-000000000001" as CompanyId, permissions },
    next,
  );
  create.execute.mockResolvedValue(success({ id: controller.employeeId, version: 0 }));
  owners.push(controller);
  controller.activate();
  return { controller, create, next };
}
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
describe("employee creation ownership", () => {
  it("freezes the new identities and payload, excludes double submission and acknowledges without another read", async () => {
    const f = fixture();
    const held = pending<Result<MutationReceipt>>();
    f.create.execute.mockReturnValueOnce(held.promise);
    const draft = { ...fields };
    const saving = f.controller.save(draft);
    draft.legalName = "Changed while pending";
    await f.controller.save(draft);
    await f.controller.retrySave();
    expect(f.create.execute).toHaveBeenCalledOnce();
    const submitted = f.create.execute.mock.lastCall?.[2];
    expect(submitted).toMatchObject({ ...fields, employeeId: f.controller.employeeId });
    expect(submitted?.personId).not.toBe(submitted?.employeeId);
    expect(Object.isFrozen(submitted)).toBe(true);
    held.resolve(success({ id: f.controller.employeeId, version: 0 }));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      operationId: null,
      startDate: fields.startDate,
      receipt: { id: f.controller.employeeId, version: 0 },
    });
    await f.controller.save(fields);
    expect(f.create.execute).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledTimes(3);
  });
  it("retains an uncertain create through later MFA and validation rejections until its original receipt is recovered", async () => {
    const f = fixture();
    f.create.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("data_conflict"));
    await f.controller.save(fields);
    const first = f.create.execute.mock.lastCall;
    await f.controller.save({ ...fields, employeeNumber: "DIFFERENT" });
    expect(f.create.execute).toHaveBeenCalledOnce();
    for (let index = 0; index < 2; index += 1) {
      await f.controller.retrySave();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      expect(f.create.execute.mock.lastCall?.[1]).toBe(first?.[1]);
      expect(f.create.execute.mock.lastCall?.[2]).toBe(first?.[2]);
    }
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(3);
  });
  it("allows correction only after a definite first rejection, with a new operation and the same resource identities", async () => {
    const f = fixture();
    f.create.execute.mockResolvedValueOnce(failed("invalid_person"));
    await f.controller.save(fields);
    const first = f.create.execute.mock.lastCall;
    expect(f.controller.getSnapshot().stage).toBe("editing");
    await f.controller.save({ ...fields, nationality: "SG" });
    const second = f.create.execute.mock.lastCall;
    expect(second?.[1]).not.toBe(first?.[1]);
    expect(second?.[2]).toMatchObject({
      employeeId: first?.[2].employeeId,
      personId: first?.[2].personId,
      nationality: "SG",
    });
    expect(f.next).toHaveBeenCalledTimes(4);
  });
  it.each([false, true])(
    "cancels on disposal and rejects late completion (error: %s) after reactivation",
    async (rejects) => {
      const f = fixture();
      const held = pending<Result<MutationReceipt>>();
      f.create.execute.mockReturnValueOnce(held.promise);
      const saving = f.controller.save(fields);
      const signal = f.create.execute.mock.lastCall?.[3];
      f.controller.deactivate();
      f.controller.activate();
      expect(signal?.aborted).toBe(true);
      if (rejects) held.reject(new Error("PRIVATE LATE FAILURE"));
      else held.resolve(success({ id: f.controller.employeeId, version: 0 }));
      await saving;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );
  it("does not expose a writable form or invoke a use case without creation authority", async () => {
    const f = fixture(["people.read"]);
    await f.controller.save(fields);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      failure: { code: "access_denied" },
    });
    expect(f.create.execute).not.toHaveBeenCalled();
  });
});
