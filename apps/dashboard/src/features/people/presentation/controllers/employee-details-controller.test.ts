import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { Employee, EmployeeId } from "../../domain/entities/employee";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeDetailsController } from "./employee-details-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["people.read"],
};
const employee: Employee = {
  id: "40000000-0000-4000-8000-000000000001" as EmployeeId,
  companyId: access.companyId,
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
  version: 0,
  appliedRevision: 0,
};

describe("employee detail ownership", () => {
  it("drops the enclosing record on a child's company or session rejection without an automatic retry", async () => {
    const execute = vi
      .fn<PeopleUseCases["loadEmployee"]["execute"]>()
      .mockResolvedValue(success(employee));
    const controller = new EmployeeDetailsController(
      { execute },
      access,
      employee.id,
      "2026-10-01",
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().employee).toBe(employee));
    for (const code of ["connection_unavailable", "access_denied", "invalid_page"])
      controller.reportScopeFailure({ code, fields: {}, parameters: {} });
    expect(controller.getSnapshot().employee).toBe(employee);
    controller.reportScopeFailure({ code: "company_access_denied", fields: {}, parameters: {} });
    expect(controller.getSnapshot()).toMatchObject({
      employee: null,
      failure: { code: "company_access_denied" },
    });
    expect(execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
    controller.reportScopeFailure({ code: "session_revoked", fields: {}, parameters: {} });
    expect(controller.getSnapshot().stage).toBe("idle");
  });
  it("cancels a pending date-specific read and cannot restore it after access loss", async () => {
    let resolve!: (result: Result<Employee>) => void;
    const held = new Promise<Result<Employee>>((done) => {
      resolve = done;
    });
    const execute = vi
      .fn<PeopleUseCases["loadEmployee"]["execute"]>()
      .mockReturnValueOnce(held)
      .mockResolvedValue(failed("employee_not_found"));
    const controller = new EmployeeDetailsController(
      { execute },
      access,
      employee.id,
      "2026-10-01",
    );
    controller.activate();
    expect(execute.mock.lastCall?.slice(0, 3)).toEqual([access, employee.id, "2026-10-01"]);
    const signal = execute.mock.lastCall?.[3];
    controller.deactivate();
    controller.activate();
    expect(signal?.aborted).toBe(true);
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    resolve(success(employee));
    await Promise.resolve();
    expect(controller.getSnapshot()).toMatchObject({
      employee: null,
      failure: { code: "employee_not_found" },
    });
    controller.deactivate();
  });
  it("drops personal data before refresh, contains unexpected failures and releases observers", async () => {
    const execute = vi
      .fn<PeopleUseCases["loadEmployee"]["execute"]>()
      .mockResolvedValueOnce(success(employee))
      .mockRejectedValue(new Error("PRIVATE ADAPTER FAILURE"));
    const controller = new EmployeeDetailsController(
      { execute },
      access,
      employee.id,
      "2026-10-01",
    );
    const observer = vi.fn();
    const remove = controller.subscribe(observer);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().employee).toBe(employee));
    const refresh = controller.refresh();
    expect(controller.getSnapshot().employee).toBeNull();
    await refresh;
    expect(controller.getSnapshot().failure?.code).toBe("unexpected_error");
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
    expect(execute).toHaveBeenCalledTimes(2);
    remove();
    observer.mockClear();
    controller.deactivate();
    await controller.refresh();
    expect(observer).not.toHaveBeenCalled();
    expect(execute).toHaveBeenCalledTimes(2);
  });
});
