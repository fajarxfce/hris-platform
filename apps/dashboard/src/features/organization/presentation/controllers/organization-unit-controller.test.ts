import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { OrganizationUnitId } from "../../domain/entities/organization-unit";
import type { OrganizationUnitDetails } from "../../domain/entities/organization-unit-details";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import { OrganizationUnitController } from "./organization-unit-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "50000000-0000-4000-8000-000000000001" as OrganizationUnitId;
const access = { companyId, permissions: ["company.read"] };
const details: OrganizationUnitDetails = {
  companyId,
  unit: {
    id,
    companyId,
    code: "SALES",
    name: "Sales",
    kind: "DEPARTMENT",
    parentId: null,
    timezone: null,
    active: true,
    version: 0,
  },
  parent: null,
};
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("organization detail ownership", () => {
  it("ignores late data and errors from an old request after disposal and remount", async () => {
    for (const rejects of [false, true]) {
      const old = pending<Result<OrganizationUnitDetails>>();
      const execute = vi
        .fn<OrganizationUseCases["loadUnit"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(details));
      const controller = new OrganizationUnitController({ execute }, access, id);
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      const signal = execute.mock.lastCall?.[2];
      controller.deactivate();
      expect(signal?.aborted).toBe(true);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().details).toBe(details));
      if (rejects) old.reject(new Error("PRIVATE LATE ERROR"));
      else old.resolve(failed("company_access_denied"));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", details, failure: null });
      controller.deactivate();
      expect(controller.getSnapshot().details).toBeNull();
    }
  });
  it("drops stale parent and unit data together, requires explicit retry and stops listeners", async () => {
    const waiting = pending<Result<OrganizationUnitDetails>>();
    const execute = vi
      .fn<OrganizationUseCases["loadUnit"]["execute"]>()
      .mockResolvedValueOnce(success(details))
      .mockReturnValueOnce(waiting.promise)
      .mockResolvedValue(failed("mfa_required"));
    const controller = new OrganizationUnitController({ execute }, access, id);
    const notify = vi.fn();
    const unsubscribe = controller.subscribe(notify);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().details).toBe(details));
    const refreshing = controller.refresh();
    expect(controller.getSnapshot().details).toBeNull();
    waiting.resolve(failed("connection_unavailable"));
    await refreshing;
    expect(execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().details).toBeNull();
    unsubscribe();
    const observed = notify.mock.calls.length;
    await controller.refresh();
    expect(controller.getSnapshot().failure?.code).toBe("mfa_required");
    expect(notify).toHaveBeenCalledTimes(observed);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(3);
  });
});
