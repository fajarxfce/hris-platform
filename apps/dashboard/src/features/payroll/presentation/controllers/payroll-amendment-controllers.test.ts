import { afterEach, describe, expect, it, vi } from "vitest";
import { amendmentDetails, amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import type { PayrollAmendmentPage } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentInspection } from "../../domain/entities/payroll-amendment-search";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollAmendmentController } from "./payroll-amendment-controller";
import { PayrollAmendmentsController } from "./payroll-amendments-controller";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend.review"],
  },
  owned = new Set<{ deactivate: () => void }>();
afterEach(() => {
  for (const controller of owned) controller.deactivate();
  owned.clear();
});
function gate<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((ok, no) => {
    resolve = ok;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function flush() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}
function inspection(): PayrollAmendmentInspection {
  const current = toPayrollAmendmentDetails(amendmentDetails(0), access.companyId, amendmentId());
  return { current, selected: current.amendment, history: current.history, calculation: null };
}
function fixture() {
  const execute = vi
      .fn<PayrollUseCases["loadAmendment"]["execute"]>()
      .mockResolvedValue(success(inspection())),
    controller = new PayrollAmendmentController({ execute }, access, amendmentId(), {
      revision: 0,
      after: null,
    });
  owned.add(controller);
  return { execute, controller };
}
describe("scoped amendment readers", () => {
  it("one activation owns one load and denied refresh clears every private section", async () => {
    const { execute, controller } = fixture();
    controller.activate();
    controller.activate();
    await flush();
    expect(execute).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().stage).toBe("ready");
    execute.mockResolvedValueOnce(failed("payroll_amendment_self_preparation_forbidden"));
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      data: null,
      failure: { code: "payroll_amendment_self_preparation_forbidden" },
    });
  });
  it.each(["result", "failure"])(
    "a replaced late %s cannot overwrite its successor",
    async (outcome) => {
      const { execute, controller } = fixture(),
        held = gate<Result<PayrollAmendmentInspection>>();
      execute.mockReturnValueOnce(held.promise);
      controller.activate();
      const previous = execute.mock.calls[0]?.[3];
      await controller.refresh();
      expect(previous?.aborted).toBe(true);
      const accepted = controller.getSnapshot();
      if (outcome === "result") held.resolve(failed("access_denied"));
      else held.reject(new Error("late private failure"));
      await flush();
      expect(controller.getSnapshot()).toBe(accepted);
    },
  );
  it("disposal cancels pending work, releases subscribers and permits safe reactivation", async () => {
    const { execute, controller } = fixture(),
      held = gate<Result<PayrollAmendmentInspection>>(),
      listener = vi.fn();
    execute.mockReturnValueOnce(held.promise);
    const unsubscribe = controller.subscribe(listener);
    controller.activate();
    const signal = execute.mock.calls[0]?.[3];
    unsubscribe();
    controller.deactivate();
    const calls = listener.mock.calls.length;
    expect(signal?.aborted).toBe(true);
    held.resolve(success(inspection()));
    await flush();
    expect(controller.getSnapshot().data).toBeNull();
    expect(listener).toHaveBeenCalledTimes(calls);
    controller.activate();
    await flush();
    expect(controller.getSnapshot().stage).toBe("ready");
  });
  it("unexpected current failure is sanitized and explicit refresh recovers", async () => {
    const { execute, controller } = fixture();
    execute.mockRejectedValueOnce(new Error("PRIVATE TOKEN"));
    controller.activate();
    await flush();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      data: null,
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("TOKEN");
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("ready");
  });
  it("directory replacement owns its cursor and discards a late page", async () => {
    const held = gate<Result<PayrollAmendmentPage>>(),
      execute = vi
        .fn<PayrollUseCases["listAmendments"]["execute"]>()
        .mockReturnValueOnce(held.promise)
        .mockResolvedValue(success({ items: [], nextCursor: null })),
      controller = new PayrollAmendmentsController({ execute }, access, {
        year: 2026,
        query: "",
        employeeId: null,
        status: null,
        after: null,
      });
    owned.add(controller);
    controller.activate();
    const previous = execute.mock.calls[0]?.[2];
    await controller.refresh();
    expect(previous?.aborted).toBe(true);
    held.reject(new Error("old page"));
    await flush();
    expect(controller.getSnapshot().stage).toBe("ready");
    controller.deactivate();
    expect(controller.getSnapshot().data).toBeNull();
  });
});
