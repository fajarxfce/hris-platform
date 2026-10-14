import { afterEach, assert, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationDetails,
  calculationEmployee,
  calculationEmployeeId,
  calculationFields,
  calculationId,
  calculationPeriod,
  calculationReview,
  calculationRun,
} from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  toPayrollCalculationReview,
  toPayrollRunDetails,
} from "../../data/mappers/payroll-calculation-mapper";
import { toPayrollRunEmployee } from "../../data/mappers/payroll-run-employee-mapper";
import type { PayrollRunDetails } from "../../domain/entities/payroll-run-details";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollCalculationStartController } from "./payroll-calculation-start-controller";
import { PayrollCalculationsController } from "./payroll-calculations-controller";
import { PayrollRunController } from "./payroll-run-controller";
import { PayrollRunEmployeeController } from "./payroll-run-employee-controller";
import { PayrollRunRecoveryController } from "./payroll-run-recovery-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.calculate"],
};
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
function readRun(processing = false) {
  return toPayrollRunDetails(
    calculationDetails(
      calculationRun(
        processing ? { status: "PROCESSING", version: 0, processed: 0, succeeded: 0 } : {},
      ),
    ),
    access.companyId,
    calculationId,
    null,
    50,
  );
}
function actions() {
  const review = toPayrollCalculationReview(
      calculationReview(),
      access.companyId,
      calculationPeriod,
    ),
    details = readRun(),
    employee = toPayrollRunEmployee(
      calculationEmployee(),
      access.companyId,
      calculationId,
      calculationEmployeeId(),
    );
  return {
    loadCalculations: {
      execute: vi
        .fn<PayrollUseCases["loadCalculations"]["execute"]>()
        .mockResolvedValue(success({ review, runs: [details.run] })),
    },
    reviewCalculation: {
      execute: vi
        .fn<PayrollUseCases["reviewCalculation"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    loadRun: {
      execute: vi.fn<PayrollUseCases["loadRun"]["execute"]>().mockResolvedValue(success(details)),
    },
    loadRunEmployee: {
      execute: vi
        .fn<PayrollUseCases["loadRunEmployee"]["execute"]>()
        .mockResolvedValue(success(employee)),
    },
    reviewRunRecovery: {
      execute: vi
        .fn<PayrollUseCases["reviewRunRecovery"]["execute"]>()
        .mockResolvedValue(success(details)),
    },
    startCalculation: {
      execute: vi
        .fn<PayrollUseCases["startCalculation"]["execute"]>()
        .mockImplementation(async (_access, _id, _operation, input) =>
          success({ id: input.id, version: 0 }),
        ),
    },
    resumeCalculation: {
      execute: vi
        .fn<PayrollUseCases["resumeCalculation"]["execute"]>()
        .mockResolvedValue(success({ id: calculationId, version: 2 })),
    },
    abandonCalculation: {
      execute: vi
        .fn<PayrollUseCases["abandonCalculation"]["execute"]>()
        .mockResolvedValue(success({ id: calculationId, version: 2 })),
    },
  };
}
function scenario(mode: "start" | "resume" | "abandon") {
  const api = actions();
  let next = 0;
  const id = () => `ec900000-0000-4000-8000-${String(++next).padStart(12, "0")}`;
  if (mode === "start") {
    const controller = new PayrollCalculationStartController(api, access, calculationPeriod, id);
    return {
      api,
      controller,
      save: () => controller.save(calculationFields),
      command: api.startCalculation.execute,
    };
  }
  const controller = new PayrollRunRecoveryController(api, access, calculationId, mode, id);
  return {
    api,
    controller,
    save: () => controller.save("Correct reviewed input"),
    command: mode === "resume" ? api.resumeCalculation.execute : api.abandonCalculation.execute,
  };
}
afterEach(() => vi.useRealTimers());
describe("calculation command ownership", () => {
  it("treats the server's tax-month rejection as a definite failure and permits a corrected command", async () => {
    const { controller, save, command } = scenario("start");
    controller.activate();
    await flush();
    command.mockResolvedValueOnce(failed("payroll_tax_month_review_required"));
    await save();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      failure: { code: "payroll_tax_month_review_required" },
      operationId: null,
    });
    await controller.retry();
    expect(command).toHaveBeenCalledTimes(1);
    await save();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(command.mock.calls[1]?.[2]).not.toBe(command.mock.calls[0]?.[2]);
    controller.deactivate();
  });
  it.each(["start", "resume", "abandon"] as const)(
    "%s retains the original command after a lost response and forbids parallel submissions",
    async (mode) => {
      const { controller, api, save, command } = scenario(mode),
        pending = gate<Result<MutationReceipt>>();
      command.mockReturnValueOnce(pending.promise);
      controller.activate();
      await flush();
      const saving = save();
      await save();
      expect(command).toHaveBeenCalledTimes(1);
      expect(controller.getSnapshot().stage).toBe("saving");
      pending.resolve(failed("connection_unavailable"));
      await saving;
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
      const original = command.mock.calls[0];
      await controller.refresh();
      expect(
        api.reviewCalculation.execute.mock.calls.length +
          api.reviewRunRecovery.execute.mock.calls.length,
      ).toBe(1);
      command.mockResolvedValueOnce(failed("recent_authentication_required"));
      await controller.retry();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
      await controller.retry();
      expect(controller.getSnapshot().stage).toBe("saved");
      expect(command.mock.calls[1]?.slice(0, 4)).toEqual(original?.slice(0, 4));
      expect(command.mock.calls[2]?.slice(0, 4)).toEqual(original?.slice(0, 4));
      controller.deactivate();
    },
  );
  it.each(["start", "resume", "abandon"] as const)(
    "%s discards a late committed acknowledgement after its owner leaves",
    async (mode) => {
      const { controller, save, command } = scenario(mode),
        pending = gate<Result<MutationReceipt>>();
      command.mockReturnValueOnce(pending.promise);
      controller.activate();
      await flush();
      const writing = save(),
        signal = command.mock.calls[0]?.[4];
      controller.deactivate();
      expect(signal?.aborted).toBe(true);
      pending.resolve(success({ id: calculationId, version: 2 }));
      await writing;
      expect(controller.getSnapshot().stage).toBe("loading");
      expect(controller.getSnapshot().receipt).toBeNull();
    },
  );
  it("requires explicit reload before constructing a new command after a policy conflict", async () => {
    const { controller, api, save, command } = scenario("start");
    command.mockResolvedValueOnce(failed("stale_policy_version"));
    controller.activate();
    await flush();
    await save();
    expect(controller.getSnapshot().stage).toBe("conflict");
    await save();
    expect(command).toHaveBeenCalledTimes(1);
    const newer = calculationReview();
    assert(newer.policy);
    newer.policy.version = 1;
    newer.policy.appliedRevision = 1;
    api.reviewCalculation.execute.mockResolvedValueOnce(
      success(toPayrollCalculationReview(newer, access.companyId, calculationPeriod)),
    );
    await controller.refresh();
    await save();
    expect(command.mock.calls[1]?.[3]).toMatchObject({ expectedPolicyVersion: 1 });
    expect(command.mock.calls[1]?.[2]).not.toBe(command.mock.calls[0]?.[2]);
    controller.deactivate();
  });
  it("rejects an out-of-month date before creating an operation and clears private reviews on denial", async () => {
    const api = actions(),
      controller = new PayrollCalculationStartController(
        api,
        access,
        calculationPeriod,
        () => calculationId,
      );
    controller.activate();
    await flush();
    await controller.save({ ...calculationFields, incomeDueDate: "2026-08-31" });
    expect(api.startCalculation.execute).not.toHaveBeenCalled();
    expect(controller.getSnapshot().failure?.fields.incomeDueDate).toBe("out_of_range");
    api.startCalculation.execute.mockResolvedValueOnce(failed("access_denied"));
    await controller.save(calculationFields);
    expect(controller.getSnapshot().review).toBeNull();
    expect(controller.getSnapshot().stage).toBe("unavailable");
    controller.deactivate();
  });
});
describe("bounded run observation", () => {
  it("coalesces manual refresh ownership and ignores a late older result", async () => {
    const api = actions(),
      older = gate<Result<PayrollRunDetails>>(),
      controller = new PayrollRunController(api.loadRun, access, calculationId, null);
    api.loadRun.execute.mockReturnValueOnce(older.promise);
    controller.activate();
    const oldSignal = api.loadRun.execute.mock.calls[0]?.[3];
    await controller.refresh();
    expect(oldSignal?.aborted).toBe(true);
    older.resolve(success(readRun(true)));
    await flush();
    expect(controller.getSnapshot().details?.run.status).toBe("CALCULATED");
    controller.deactivate();
  });
  it("limits automatic polling and stops on completion without refetching another page", async () => {
    vi.useFakeTimers();
    const api = actions();
    api.loadRun.execute.mockResolvedValue(success(readRun(true)));
    const controller = new PayrollRunController(api.loadRun, access, calculationId, "50");
    controller.activate();
    await flush();
    await vi.advanceTimersByTimeAsync(125000);
    expect(api.loadRun.execute).toHaveBeenCalledTimes(31);
    expect(controller.getSnapshot().updatesPaused).toBe(true);
    await vi.advanceTimersByTimeAsync(120000);
    expect(api.loadRun.execute).toHaveBeenCalledTimes(31);
    expect(api.loadRun.execute.mock.calls.every((call) => call[2] === "50")).toBe(true);
    api.loadRun.execute.mockResolvedValueOnce(success(readRun()));
    await controller.refresh();
    await vi.advanceTimersByTimeAsync(120000);
    expect(api.loadRun.execute).toHaveBeenCalledTimes(32);
    controller.deactivate();
  });
  it("does not start while hidden and aborts a pending automatic read on hide", async () => {
    vi.useFakeTimers();
    const api = actions(),
      later = gate<Result<PayrollRunDetails>>();
    api.loadRun.execute
      .mockResolvedValueOnce(success(readRun(true)))
      .mockReturnValueOnce(later.promise);
    const controller = new PayrollRunController(api.loadRun, access, calculationId, null);
    controller.setVisible(false);
    controller.activate();
    expect(api.loadRun.execute).not.toHaveBeenCalled();
    controller.setVisible(true);
    await flush();
    await vi.advanceTimersByTimeAsync(4000);
    const signal = api.loadRun.execute.mock.calls[1]?.[3];
    controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    later.reject(new Error("private failure"));
    await flush();
    await vi.advanceTimersByTimeAsync(120000);
    expect(api.loadRun.execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().failure).toBeNull();
    controller.deactivate();
  });
  it("retains a visible snapshot after a transient failure but stops automatic reads", async () => {
    vi.useFakeTimers();
    const api = actions();
    api.loadRun.execute
      .mockResolvedValueOnce(success(readRun(true)))
      .mockResolvedValueOnce(failed("connection_unavailable"));
    const controller = new PayrollRunController(api.loadRun, access, calculationId, null);
    controller.activate();
    await flush();
    await vi.advanceTimersByTimeAsync(4000);
    expect(controller.getSnapshot().details).not.toBeNull();
    expect(controller.getSnapshot().stage).toBe("unavailable");
    await vi.advanceTimersByTimeAsync(120000);
    expect(api.loadRun.execute).toHaveBeenCalledTimes(2);
    controller.deactivate();
  });
  it("withdraws the visible snapshot when access or MFA is no longer admitted", async () => {
    vi.useFakeTimers();
    const api = actions();
    api.loadRun.execute
      .mockResolvedValueOnce(success(readRun(true)))
      .mockResolvedValueOnce(failed("mfa_required"));
    const controller = new PayrollRunController(api.loadRun, access, calculationId, null);
    controller.activate();
    await flush();
    await vi.advanceTimersByTimeAsync(4000);
    expect(controller.getSnapshot().details).toBeNull();
    expect(controller.getSnapshot().failure?.code).toBe("mfa_required");
    await vi.advanceTimersByTimeAsync(120000);
    expect(api.loadRun.execute).toHaveBeenCalledTimes(2);
    controller.deactivate();
  });
  it("stops timers and late failures on disposal", async () => {
    vi.useFakeTimers();
    const api = actions(),
      pending = gate<Result<PayrollRunDetails>>();
    api.loadRun.execute
      .mockResolvedValueOnce(success(readRun(true)))
      .mockReturnValueOnce(pending.promise);
    const controller = new PayrollRunController(api.loadRun, access, calculationId, null);
    controller.activate();
    await flush();
    await vi.advanceTimersByTimeAsync(4000);
    controller.deactivate();
    pending.reject(new Error("old scope"));
    await flush();
    await vi.advanceTimersByTimeAsync(120000);
    expect(controller.getSnapshot().details).toBeNull();
    expect(controller.getSnapshot().failure).toBeNull();
    expect(api.loadRun.execute).toHaveBeenCalledTimes(2);
  });
  it("the overview and employee detail own separate reads and clear on departure", async () => {
    const api = actions(),
      overview = new PayrollCalculationsController(api.loadCalculations, access, calculationPeriod),
      employee = new PayrollRunEmployeeController(
        api.loadRunEmployee,
        access,
        calculationId,
        calculationEmployeeId(),
      );
    overview.activate();
    await flush();
    expect(api.loadRunEmployee.execute).not.toHaveBeenCalled();
    employee.activate();
    await flush();
    expect(employee.getSnapshot().employee?.item.target.employeeId).toBe(calculationEmployeeId());
    overview.deactivate();
    employee.deactivate();
    expect(overview.getSnapshot().data).toBeNull();
    expect(employee.getSnapshot().employee).toBeNull();
  });
});
