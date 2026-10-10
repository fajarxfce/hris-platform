import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTemplateController } from "./lifecycle-template-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["people.lifecycle.read"],
};
const search = "90000000-abcd-4000-8000-000000000001" as LifecycleTemplateId;
const page: LifecycleTemplate = {
  id: search,
  companyId: access.companyId,
  code: "CHECKLIST",
  name: "Checklist",
  kind: "ONBOARDING",
  active: true,
  version: 0,
  tasks: [{ key: "equipment", title: "Equipment", required: true, dueDays: 0 }],
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

describe("lifecycle template detail ownership", () => {
  it("retains the applied filters and ignores superseded data or late failures", async () => {
    for (const rejects of [false, true]) {
      const old = pending<Result<LifecycleTemplate>>();
      const execute = vi
        .fn<LifecycleUseCases["loadTemplate"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(page));
      const controller = new LifecycleTemplateController({ execute }, access, search);
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
      expect(controller.getSnapshot()).toEqual({ stage: "ready", template: page, failure: null });
      controller.deactivate();
    }
  });
  it("clears data on refresh or disposal and never lets an old lifetime repopulate a remount", async () => {
    const old = pending<Result<LifecycleTemplate>>();
    const execute = vi
      .fn<LifecycleUseCases["loadTemplate"]["execute"]>()
      .mockResolvedValueOnce(success(page))
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(failed("company_access_denied"));
    const controller = new LifecycleTemplateController({ execute }, access, search);
    const changed = vi.fn();
    const unsubscribe = controller.subscribe(changed);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().template).toBe(page));
    const refreshing = controller.refresh();
    expect(controller.getSnapshot().template).toBeNull();
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
    expect(controller.getSnapshot().template).toBeNull();
    expect(changed).toHaveBeenCalledTimes(observed);
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(3);
  });
});
