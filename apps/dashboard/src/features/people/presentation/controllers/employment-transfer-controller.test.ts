import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentTransferReview } from "../../domain/entities/employment-transfer-review";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  EmploymentTransferController,
  type TransferEditableFields,
} from "./employment-transfer-controller";

const source = "10000000-0000-4000-8000-000000000001" as CompanyId;
const target = "10000000-0000-4000-8000-000000000002" as CompanyId;
const other = "10000000-0000-4000-8000-000000000003" as CompanyId;
const employeeId = "40000000-0000-4000-8000-000000000001" as EmployeeId;
const review: EmploymentTransferReview = {
  employee: {
    id: employeeId,
    companyId: source,
    employeeNumber: "NORTH-001",
    legalName: "Employee One",
    email: null,
    version: 7,
    appliedRevision: 0,
    terms: {
      effectiveFrom: "2026-01-01",
      startDate: "2026-01-01",
      endDate: null,
      contract: "PERMANENT",
      status: "ACTIVE",
    },
  },
  sourceCompany: { id: source, code: "NORTH", name: "North Company", timezone: "Asia/Jakarta" },
  targetCompany: { id: target, code: "SOUTH", name: "South Company", timezone: "Asia/Makassar" },
  sourceDate: "2026-10-01",
  targetDate: "2026-10-01",
  evaluatedAt: "2026-10-01T12:00:00Z",
  targetPermissions: ["people.manage", "people.transfer"],
};
const fields: TransferEditableFields = {
  employeeNumber: "SOUTH-001",
  reason: "Approved transfer",
  terms: {
    endDate: null,
    contract: "PERMANENT",
    status: "ACTIVE",
    branchId: null,
    departmentId: null,
    positionId: null,
    costCenterId: null,
    managerId: null,
  },
};
const owners: EmploymentTransferController[] = [];
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
function fixture(permissions = ["people.read", "people.manage", "people.transfer"]) {
  const loadEmploymentTransferReview = {
    execute: vi
      .fn<PeopleUseCases["loadEmploymentTransferReview"]["execute"]>()
      .mockResolvedValue(success(review)),
  };
  const transferEmployee = {
    execute: vi
      .fn<PeopleUseCases["transferEmployee"]["execute"]>()
      .mockImplementation(async (_access, _operation, input) =>
        success({ id: input.targetEmploymentId, version: 0 }),
      ),
  };
  let issued = 0;
  const next = vi.fn(() => `60000000-0000-4000-8000-${String(++issued).padStart(12, "0")}`);
  const controller = new EmploymentTransferController(
    { loadEmploymentTransferReview, transferEmployee },
    { companyId: source, permissions },
    employeeId,
    next,
  );
  owners.push(controller);
  controller.activate();
  return { controller, loadEmploymentTransferReview, transferEmployee, next };
}

describe("employment transfer ownership", () => {
  it("requires explicit confirmation, pins the server date and version, and freezes the proposal", async () => {
    const f = fixture();
    expect(f.loadEmploymentTransferReview.execute).not.toHaveBeenCalled();
    await f.controller.selectTarget(target);
    const input = {
      ...fields,
      terms: { ...fields.terms, startDate: "2020-01-01", effectiveFrom: "2020-01-01" },
    };
    f.controller.requestTransfer(input);
    expect(f.controller.getSnapshot().stage).toBe("confirming");
    expect(f.transferEmployee.execute).not.toHaveBeenCalled();
    input.reason = "Changed after review";
    input.terms.endDate = "2027-01-01";
    const held = pending<Result<MutationReceipt>>();
    f.transferEmployee.execute.mockReturnValueOnce(held.promise);
    const sending = f.controller.confirmTransfer();
    f.controller.dismissTransfer();
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    await f.controller.retryTransfer();
    await f.controller.selectTarget(other);
    await f.controller.refresh();
    const submitted = f.transferEmployee.execute.mock.lastCall?.[2];
    expect(submitted).toEqual({
      employeeId,
      targetCompanyId: target,
      targetEmploymentId: "60000000-0000-4000-8000-000000000002",
      expectedVersion: 7,
      employeeNumber: fields.employeeNumber,
      reason: fields.reason,
      terms: { ...fields.terms, startDate: review.targetDate, effectiveFrom: review.targetDate },
    });
    expect(Object.isFrozen(submitted)).toBe(true);
    expect(Object.isFrozen(submitted?.terms)).toBe(true);
    expect(f.transferEmployee.execute).toHaveBeenCalledOnce();
    expect(f.loadEmploymentTransferReview.execute).toHaveBeenCalledOnce();
    held.resolve(success({ id: submitted?.targetEmploymentId ?? "", version: 0 }));
    await sending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      operationId: null,
      failure: null,
    });
  });

  it("dismisses an unsent proposal without writing and permits a new destination review", async () => {
    const f = fixture();
    await f.controller.selectTarget(target);
    f.controller.requestTransfer(fields);
    f.controller.dismissTransfer();
    await f.controller.confirmTransfer();
    expect(f.controller.getSnapshot().stage).toBe("editing");
    expect(f.transferEmployee.execute).not.toHaveBeenCalled();
    await f.controller.selectTarget("");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "choosing",
      review: null,
      targetCompanyId: "",
    });
  });

  it("reuses both original identifiers and the exact proposal after a lost response even if later attempts are rejected", async () => {
    const f = fixture();
    await f.controller.selectTarget(target);
    f.transferEmployee.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    const first = f.transferEmployee.execute.mock.calls[0];
    f.controller.dismissTransfer();
    f.controller.requestTransfer({ ...fields, reason: "Replacement" });
    await f.controller.selectTarget(other);
    await f.controller.refresh();
    for (let attempt = 0; attempt < 2; attempt += 1) {
      await f.controller.retryTransfer();
      expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
      expect(f.transferEmployee.execute.mock.lastCall?.[1]).toBe(first?.[1]);
      expect(f.transferEmployee.execute.mock.lastCall?.[2]).toBe(first?.[2]);
    }
    await f.controller.retryTransfer();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.loadEmploymentTransferReview.execute).toHaveBeenCalledOnce();
  });

  it.each([
    "stale_version",
    "transfer_requires_current_company_date",
    "source_employment_not_transferable",
  ])("requires a fresh review after %s and uses its date and version", async (code) => {
    const f = fixture();
    await f.controller.selectTarget(target);
    f.transferEmployee.execute.mockResolvedValueOnce(failed(code));
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    expect(f.transferEmployee.execute).toHaveBeenCalledOnce();
    f.loadEmploymentTransferReview.execute.mockResolvedValueOnce(
      success({
        ...review,
        employee: { ...review.employee, version: 9 },
        sourceDate: "2026-10-02",
        targetDate: "2026-10-02",
      }),
    );
    await f.controller.refresh();
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    expect(f.transferEmployee.execute.mock.lastCall?.[2]).toMatchObject({
      expectedVersion: 9,
      terms: { startDate: "2026-10-02", effectiveFrom: "2026-10-02" },
    });
  });

  it("retains the review for a definite correctable rejection", async () => {
    const f = fixture();
    await f.controller.selectTarget(target);
    f.transferEmployee.execute.mockResolvedValueOnce(failed("destination_membership_required"));
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      review,
      operationId: null,
    });
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });

  it("does not acquire cross-company data without the required source grants", async () => {
    const f = fixture(["people.read", "people.manage"]);
    await f.controller.selectTarget(target);
    f.controller.requestTransfer(fields);
    await f.controller.confirmTransfer();
    expect(f.controller.getSnapshot().stage).toBe("unavailable");
    expect(f.loadEmploymentTransferReview.execute).not.toHaveBeenCalled();
    expect(f.transferEmployee.execute).not.toHaveBeenCalled();
  });

  it.each([false, true])(
    "cancels old destination reads and rejects late outcomes (failure: %s)",
    async (rejects) => {
      const f = fixture();
      const held = pending<Result<EmploymentTransferReview>>();
      f.loadEmploymentTransferReview.execute.mockReturnValueOnce(held.promise);
      const old = f.controller.selectTarget(target);
      const signal = f.loadEmploymentTransferReview.execute.mock.lastCall?.[3];
      f.loadEmploymentTransferReview.execute.mockResolvedValueOnce(
        success({ ...review, targetCompany: { ...review.targetCompany, id: other } }),
      );
      await f.controller.selectTarget(other);
      expect(signal?.aborted).toBe(true);
      if (rejects) held.reject(new Error("PRIVATE LATE ERROR"));
      else held.resolve(success(review));
      await old;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        targetCompanyId: other,
        review: { targetCompany: { id: other } },
        failure: null,
      });
    },
  );

  it.each([false, true])(
    "disposal clears the proposal and fences a late command after reactivation (failure: %s)",
    async (rejects) => {
      const f = fixture();
      await f.controller.selectTarget(target);
      const held = pending<Result<MutationReceipt>>();
      f.transferEmployee.execute.mockReturnValueOnce(held.promise);
      f.controller.requestTransfer(fields);
      const sending = f.controller.confirmTransfer();
      const signal = f.transferEmployee.execute.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      expect(f.controller.getSnapshot().review).toBeNull();
      f.controller.activate();
      await f.controller.selectTarget(target);
      if (rejects) held.reject(new Error("PRIVATE LATE ERROR"));
      else held.resolve(success({ id: employeeId, version: 0 }));
      await sending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );
});
