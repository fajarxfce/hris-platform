import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { OrganizationUnitPage } from "../../domain/entities/organization-unit-page";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import { OrganizationDirectoryController } from "./organization-directory-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["company.read"],
};
const search = { query: "Sales", kind: "DEPARTMENT", active: "true", after: null };
const page: OrganizationUnitPage = { items: [], nextCursor: null };
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("organization directory ownership", () => {
  it("retains the applied filters and ignores superseded data or late failures", async () => {
    for (const rejects of [false, true]) {
      const old = pending<Result<OrganizationUnitPage>>();
      const execute = vi
        .fn<OrganizationUseCases["loadUnits"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(page));
      const input = { ...search };
      const controller = new OrganizationDirectoryController({ execute }, access, input);
      input.query = "mutated";
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
  it("clears data on refresh or disposal and never lets an old lifetime repopulate a remount", async () => {
    const old = pending<Result<OrganizationUnitPage>>();
    const execute = vi
      .fn<OrganizationUseCases["loadUnits"]["execute"]>()
      .mockResolvedValueOnce(success(page))
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(failed("company_access_denied"));
    const controller = new OrganizationDirectoryController({ execute }, access, search);
    const changed = vi.fn();
    const unsubscribe = controller.subscribe(changed);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
    const refreshing = controller.refresh();
    expect(controller.getSnapshot().page).toBeNull();
    const signal = execute.mock.lastCall?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    unsubscribe();
    const observed = changed.mock.calls.length;
    controller.activate();
    await vi.waitFor(() =>
      expect(controller.getSnapshot().failure?.code).toBe("company_access_denied"),
    );
    old.resolve(success(page));
    await refreshing;
    expect(controller.getSnapshot().page).toBeNull();
    expect(changed).toHaveBeenCalledTimes(observed);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(3);
  });
});
