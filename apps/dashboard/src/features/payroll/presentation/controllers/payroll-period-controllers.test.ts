import { assert, describe, expect, it, vi } from "vitest";
import {
  periodCompany,
  periodCreation,
  periodDetails,
  periodEmployee,
  periodId,
} from "../../../../../tests/fixtures/payroll-periods";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollPeriodDetails } from "../../data/mappers/payroll-period-mapper";
import type { PayrollPeriodEmployees } from "../../domain/entities/payroll-period-employee";
import { ReviewPayrollPeriodCreation } from "../../domain/usecases/review-payroll-period-creation";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollPeriodCancellationController } from "./payroll-period-cancellation-controller";
import { PayrollPeriodController } from "./payroll-period-controller";
import { PayrollPeriodCreationController } from "./payroll-period-creation-controller";
import { PayrollPeriodsController } from "./payroll-periods-controller";

const access: CompanyAccess = {
  companyId: periodCompany as CompanyId,
  permissions: ["payroll.calculate"],
};
function gate<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
const ready = async () => {
  await Promise.resolve();
  await Promise.resolve();
};
function actions() {
  const detail = toPayrollPeriodDetails(periodDetails(), access.companyId, periodId, null, null);
  return {
    listPeriods: {
      execute: vi
        .fn<PayrollUseCases["listPeriods"]["execute"]>()
        .mockResolvedValue(success({ items: [detail.period], nextCursor: null })),
    },
    loadPeriod: {
      execute: vi.fn<PayrollUseCases["loadPeriod"]["execute"]>().mockResolvedValue(success(detail)),
    },
    listPeriodEmployees: {
      execute: vi
        .fn<PayrollUseCases["listPeriodEmployees"]["execute"]>()
        .mockResolvedValue(
          success({ items: [periodEmployee(), periodEmployee(2)], nextCursor: null }),
        ),
    },
    reviewPeriodCreation: new ReviewPayrollPeriodCreation(),
    createPeriod: {
      execute: vi
        .fn<PayrollUseCases["createPeriod"]["execute"]>()
        .mockImplementation(async (_access, _operation, input) =>
          success({ id: input.id, version: 0 }),
        ),
    },
    reviewPeriodCancellation: {
      execute: vi
        .fn<PayrollUseCases["reviewPeriodCancellation"]["execute"]>()
        .mockResolvedValue(success(detail)),
    },
    cancelPeriod: {
      execute: vi
        .fn<PayrollUseCases["cancelPeriod"]["execute"]>()
        .mockResolvedValue(success({ id: periodId, version: 1 })),
    },
  };
}
function creator(api: ReturnType<typeof actions>, actor = access) {
  let next = 0;
  return new PayrollPeriodCreationController(
    api,
    actor,
    () => `ea300000-0000-4000-8000-${String(++next).padStart(12, "0")}`,
  );
}
function cancellation(api: ReturnType<typeof actions>) {
  let next = 0;
  return new PayrollPeriodCancellationController(
    api,
    access,
    periodId,
    () => `ea300000-0000-4000-8000-${String(++next).padStart(12, "0")}`,
  );
}
describe("payroll period presentation ownership", () => {
  it("keeps selected employees across pages and accepts only acquired rows", async () => {
    const api = actions(),
      controller = creator(api);
    controller.activate();
    await ready();
    controller.toggle(periodEmployee().id, true);
    controller.toggle(periodEmployee(8).id, true);
    expect(controller.getSnapshot().selected.map((row) => row.id)).toEqual([periodEmployee().id]);
    api.listPeriodEmployees.execute.mockResolvedValue(
      success({ items: [periodEmployee(3)], nextCursor: null }),
    );
    await controller.search("3", null);
    controller.togglePage(true);
    expect(controller.getSnapshot().selected.map((row) => row.id)).toEqual([
      periodEmployee().id,
      periodEmployee(3).id,
    ]);
    controller.togglePage(false);
    controller.review(periodCreation());
    expect(controller.getSnapshot().stage).toBe("reviewing");
    controller.remove(periodEmployee().id);
    expect(controller.getSnapshot().review?.employeeIds).toEqual([periodEmployee().id]);
    controller.deactivate();
  });
  it("bounds 5000 selected employees and their displayed pages", async () => {
    const api = actions(),
      controller = creator(api);
    controller.activate();
    await ready();
    for (let page = 0; page < 100; page++) {
      api.listPeriodEmployees.execute.mockResolvedValue(
        success({
          items: Array.from({ length: 50 }, (_, i) => periodEmployee(page * 50 + i + 1)),
          nextCursor: null,
        }),
      );
      await controller.search(String(page), null);
      controller.togglePage(true);
    }
    expect(controller.getSnapshot().selected).toHaveLength(5000);
    controller.selectPage(99);
    expect(controller.getSnapshot().selectedPage).toBe(99);
    api.listPeriodEmployees.execute.mockResolvedValue(
      success({ items: [periodEmployee(5001)], nextCursor: null }),
    );
    await controller.search("5001", null);
    controller.togglePage(true);
    expect(controller.getSnapshot().selected).toHaveLength(5000);
    expect(controller.getSnapshot().failure?.code).toBe("invalid_payroll_period");
    controller.selectPage(100);
    expect(controller.getSnapshot().selectedPage).toBe(99);
    controller.deactivate();
  });
  it("rejects superseded discovery and late failures after disposal", async () => {
    const api = actions(),
      first = gate<Result<PayrollPeriodEmployees>>();
    api.listPeriodEmployees.execute.mockReturnValueOnce(first.promise);
    const controller = creator(api);
    controller.activate();
    await controller.search("new", null);
    expect(api.listPeriodEmployees.execute.mock.calls[0]?.[3].aborted).toBe(true);
    first.resolve(success({ items: [periodEmployee(10)], nextCursor: null }));
    await ready();
    expect(controller.getSnapshot().page?.items[0]?.id).toBe(periodEmployee().id);
    const last = gate<Result<PayrollPeriodEmployees>>();
    api.listPeriodEmployees.execute.mockReturnValueOnce(last.promise);
    const pending = controller.search("late", null);
    controller.deactivate();
    last.resolve(failed("access_denied"));
    await pending;
    expect(controller.getSnapshot().failure).toBeNull();
    expect(controller.getSnapshot().page).toBeNull();
  });
  it("clears employee evidence after confirmed access loss", async () => {
    const api = actions(),
      controller = creator(api);
    controller.activate();
    await ready();
    controller.togglePage(true);
    api.listPeriodEmployees.execute.mockResolvedValue(failed("access_denied"));
    await controller.search("", null);
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      selected: [],
      page: null,
    });
    controller.deactivate();
  });
  it("retains the original command after response loss and a rejected retry", async () => {
    const api = actions(),
      controller = creator(api);
    controller.activate();
    await ready();
    controller.togglePage(true);
    controller.review(periodCreation());
    api.createPeriod.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("payroll_period_active"));
    await controller.confirm();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    controller.edit();
    controller.remove(periodEmployee().id);
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    const calls = api.createPeriod.execute.mock.calls;
    expect(calls).toHaveLength(3);
    expect(calls[1]?.slice(0, 3)).toEqual(calls[0]?.slice(0, 3));
    expect(calls[2]?.slice(0, 3)).toEqual(calls[0]?.slice(0, 3));
    expect(Object.isFrozen(calls[0]?.[2].employeeIds)).toBe(true);
    controller.deactivate();
  });
  it("blocks duplicate submissions and discards a committed result after navigation", async () => {
    const api = actions(),
      pending = gate<Result<MutationReceipt>>(),
      controller = creator(api);
    controller.activate();
    await ready();
    controller.togglePage(true);
    controller.review(periodCreation());
    api.createPeriod.execute.mockReturnValue(pending.promise);
    const save = controller.confirm();
    await controller.confirm();
    expect(api.createPeriod.execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
    pending.resolve(success({ id: periodId, version: 0 }));
    await save;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(api.createPeriod.execute.mock.calls[0]?.[3].aborted).toBe(true);
  });
  it("hides denied employee evidence while retaining an unconfirmed original command", async () => {
    const api = actions(),
      controller = creator(api);
    controller.activate();
    await ready();
    controller.togglePage(true);
    controller.review(periodCreation());
    api.createPeriod.execute
      .mockResolvedValueOnce(failed("request_timeout"))
      .mockResolvedValueOnce(failed("access_denied"));
    await controller.confirm();
    await controller.retry();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      selected: [],
      review: null,
    });
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(api.createPeriod.execute.mock.calls[2]?.[1]).toBe(
      api.createPeriod.execute.mock.calls[0]?.[1],
    );
    controller.deactivate();
  });
  it("does not discover employees for a reader opening the creation page", async () => {
    const api = actions(),
      controller = creator(api, { ...access, permissions: ["payroll.read"] });
    controller.activate();
    await ready();
    expect(controller.getSnapshot().stage).toBe("unavailable");
    expect(api.listPeriodEmployees.execute).not.toHaveBeenCalled();
    controller.deactivate();
  });
  it("retries cancellation with the original observed version", async () => {
    const api = actions(),
      controller = cancellation(api);
    controller.activate();
    await ready();
    api.cancelPeriod.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("stale_version"));
    await controller.save("Original reason");
    await controller.retrySave();
    await controller.refresh();
    expect(api.reviewPeriodCancellation.execute).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    expect(api.cancelPeriod.execute.mock.calls.map((call) => call.slice(0, 5))).toEqual(
      Array(3).fill(api.cancelPeriod.execute.mock.calls[0]?.slice(0, 5)),
    );
    controller.deactivate();
  });
  it("requires a new cancellation review after a definite version conflict", async () => {
    const api = actions(),
      controller = cancellation(api);
    controller.activate();
    await ready();
    api.cancelPeriod.execute.mockResolvedValueOnce(failed("stale_version"));
    await controller.save("Review draft");
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.retrySave();
    expect(api.cancelPeriod.execute).toHaveBeenCalledTimes(1);
    const record = toPayrollPeriodDetails(
      periodDetails({ version: 2 }),
      access.companyId,
      periodId,
      null,
      null,
    );
    api.reviewPeriodCancellation.execute.mockResolvedValue(success(record));
    await controller.refresh();
    await controller.save("New version");
    expect(api.cancelPeriod.execute.mock.calls[1]?.[3]).toBe(2);
    expect(api.cancelPeriod.execute.mock.calls[1]?.[2]).not.toBe(
      api.cancelPeriod.execute.mock.calls[0]?.[2],
    );
    controller.deactivate();
  });
  it("stops list/detail observation and discards obsolete read results", async () => {
    const api = actions(),
      first = gate<Awaited<ReturnType<PayrollUseCases["loadPeriod"]["execute"]>>>();
    api.loadPeriod.execute.mockReturnValueOnce(first.promise);
    const detail = new PayrollPeriodController(api.loadPeriod, access, periodId, null, null);
    detail.activate();
    await detail.refresh();
    first.resolve(failed("request_failed"));
    await ready();
    expect(detail.getSnapshot().stage).toBe("ready");
    expect(api.loadPeriod.execute.mock.calls[0]?.[4].aborted).toBe(true);
    detail.deactivate();
    const list = new PayrollPeriodsController(api.listPeriods, access, {
      from: "2026-01",
      until: "2026-12",
      status: null,
      after: null,
    });
    let observations = 0;
    const unsubscribe = list.subscribe(() => {
      observations++;
    });
    list.activate();
    await ready();
    assert(list.getSnapshot().page);
    unsubscribe();
    const previous = observations;
    list.deactivate();
    expect(observations).toBe(previous);
    expect(list.getSnapshot().page).toBeNull();
  });
});
