import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeePage } from "../../domain/entities/employee-page";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmployeeDirectoryController } from "./employee-directory-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["people.read"],
};
const search = { asOf: "2026-10-01", query: "Employee", after: null };
const page: EmployeePage = { items: [], nextCursor: null };
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("employee directory request ownership", () => {
  it("keeps its applied filter snapshot and ignores superseded results or errors", async () => {
    for (const rejects of [false, true]) {
      const old = pending<Result<EmployeePage>>();
      const execute = vi
        .fn<PeopleUseCases["loadEmployees"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(page));
      const input = { ...search };
      const controller = new EmployeeDirectoryController({ execute }, access, input);
      input.query = "changed after construction";
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      expect(execute.mock.lastCall?.[1]).toEqual(search);
      const signal = execute.mock.lastCall?.[2];
      await controller.refresh();
      expect(signal?.aborted).toBe(true);
      if (rejects) old.reject(new Error("PRIVATE LATE ERROR"));
      else old.resolve(failed("access_denied"));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", page, failure: null });
      controller.deactivate();
    }
  });
  it("clears loaded data on refresh, revocation and disposal and can remount safely", async () => {
    const held = pending<Result<EmployeePage>>();
    const execute = vi
      .fn<PeopleUseCases["loadEmployees"]["execute"]>()
      .mockResolvedValueOnce(success(page))
      .mockReturnValueOnce(held.promise)
      .mockResolvedValue(failed("company_access_denied"));
    const controller = new EmployeeDirectoryController({ execute }, access, search);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
    const refresh = controller.refresh();
    expect(controller.getSnapshot().page).toBeNull();
    const signal = execute.mock.lastCall?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    controller.activate();
    await vi.waitFor(() =>
      expect(controller.getSnapshot().failure?.code).toBe("company_access_denied"),
    );
    held.resolve(success(page));
    await refresh;
    expect(controller.getSnapshot().page).toBeNull();
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(3);
  });
});
