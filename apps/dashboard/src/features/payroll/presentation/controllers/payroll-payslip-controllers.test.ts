import { afterEach, describe, expect, it, vi } from "vitest";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import {
  payslipDetails,
  payslipId,
  payslipPayments,
  payslipSummary,
} from "../../../../../tests/fixtures/payroll-payslips";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  toPayrollPaymentProgress,
  toPayrollPayslip,
  toPayrollPayslipPage,
} from "../../data/mappers/payroll-payslip-mapper";
import type { PayrollPayslip, PayrollPayslipSearch } from "../../domain/entities/payroll-payslip";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollPayslipController } from "./payroll-payslip-controller";
import { PayrollPayslipsController } from "./payroll-payslips-controller";

const access = { companyId: calculationCompany as CompanyId, permissions: ["payroll.self.read"] };
const search: PayrollPayslipSearch = {
  from: "2026-01",
  until: "2026-12",
  query: "",
  employeeId: null,
  scope: "SELF",
  after: null,
};
const owned = new Set<{ deactivate(): void }>();
afterEach(() => {
  for (const controller of owned) controller.deactivate();
  owned.clear();
});
function gate<T>() {
  let resolve!: (value: T) => void, reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function flush() {
  for (let i = 0; i < 5; i++) await Promise.resolve();
}
function actions() {
  return {
    listPayslips: {
      execute: vi
        .fn<PayrollUseCases["listPayslips"]["execute"]>()
        .mockResolvedValue(
          success(
            toPayrollPayslipPage(
              { items: [payslipSummary()], nextCursor: null },
              access.companyId,
              search,
            ),
          ),
        ),
    },
    loadPayslip: {
      execute: vi
        .fn<PayrollUseCases["loadPayslip"]["execute"]>()
        .mockResolvedValue(
          success(toPayrollPayslip(payslipDetails(), access.companyId, payslipId())),
        ),
    },
    loadPayslipPayments: {
      execute: vi
        .fn<PayrollUseCases["loadPayslipPayments"]["execute"]>()
        .mockResolvedValue(
          success(toPayrollPaymentProgress(payslipPayments(), access.companyId, payslipId())),
        ),
    },
    downloadPayslip: {
      execute: vi
        .fn<PayrollUseCases["downloadPayslip"]["execute"]>()
        .mockResolvedValue(success(undefined)),
    },
  };
}
function detail() {
  const api = actions(),
    controller = new PayrollPayslipController(api, access, payslipId(), "SELF");
  owned.add(controller);
  return { api, controller };
}
describe("payslip presentation ownership", () => {
  it("loads each projection once per activation and refreshes payments independently", async () => {
    const { api, controller } = detail();
    controller.activate();
    controller.activate();
    await flush();
    expect(controller.getSnapshot().stage).toBe("ready");
    await controller.refreshPayments();
    expect(api.loadPayslip.execute).toHaveBeenCalledOnce();
    expect(api.loadPayslipPayments.execute).toHaveBeenCalledTimes(2);
    expect(api.loadPayslipPayments.execute.mock.calls[1]?.slice(0, 3)).toEqual([
      access,
      payslipId(),
      "SELF",
    ]);
  });
  it("retains immutable detail when only payment acquisition fails", async () => {
    const { api, controller } = detail();
    controller.activate();
    await flush();
    const original = controller.getSnapshot().payslip;
    api.loadPayslipPayments.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await controller.refreshPayments();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "ready",
      payslip: original,
      paymentFailure: { code: "connection_unavailable" },
      payments: null,
    });
  });
  it("clears private detail and cancels a concurrent download when payment access is revoked", async () => {
    const { api, controller } = detail();
    controller.activate();
    await flush();
    const held = gate<Result<void>>();
    api.downloadPayslip.execute.mockReturnValueOnce(held.promise);
    const pending = controller.download();
    api.loadPayslipPayments.execute.mockResolvedValueOnce(failed("access_denied"));
    await controller.refreshPayments();
    expect(api.downloadPayslip.execute.mock.calls[0]?.[4].aborted).toBe(true);
    held.resolve(success(undefined));
    await pending;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      payslip: null,
      payments: null,
      download: "idle",
      failure: { code: "access_denied" },
    });
  });
  it.each(["success", "failure"])(
    "discards a late %s after detail replacement",
    async (outcome) => {
      const { api, controller } = detail(),
        old = gate<Result<PayrollPayslip>>();
      api.loadPayslip.execute.mockReturnValueOnce(old.promise);
      controller.activate();
      await controller.refresh();
      expect(api.loadPayslip.execute.mock.calls[0]?.[3].aborted).toBe(true);
      if (outcome === "success")
        old.resolve(success(toPayrollPayslip(payslipDetails(2), access.companyId, payslipId(2))));
      else old.reject(new Error("late failure"));
      await flush();
      expect(controller.getSnapshot().payslip?.summary.id).toBe(payslipId());
      expect(api.loadPayslipPayments.execute).toHaveBeenCalledOnce();
    },
  );
  it("pins the requested document language and admits only one download at a time", async () => {
    const { api, controller } = detail();
    controller.activate();
    await flush();
    controller.selectDocumentLanguage("EN");
    const held = gate<Result<void>>();
    api.downloadPayslip.execute.mockReturnValueOnce(held.promise);
    const pending = controller.download();
    controller.selectDocumentLanguage("ID");
    await controller.download();
    expect(api.downloadPayslip.execute).toHaveBeenCalledOnce();
    expect(api.downloadPayslip.execute.mock.calls[0]?.[3]).toBe("EN");
    held.resolve(failed("payroll_pdf_capacity"));
    await pending;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "ready",
      download: "failed",
      documentLanguage: "EN",
    });
    await flush();
    expect(api.downloadPayslip.execute).toHaveBeenCalledOnce();
    await controller.download();
    expect(controller.getSnapshot().download).toBe("saved");
  });
  it("discards download completion and pending payments when the owner leaves", async () => {
    const { api, controller } = detail();
    controller.activate();
    await flush();
    const held = gate<Result<void>>();
    api.downloadPayslip.execute.mockReturnValueOnce(held.promise);
    const pending = controller.download();
    const listener = vi.fn(),
      unsubscribe = controller.subscribe(listener);
    unsubscribe();
    controller.deactivate();
    expect(api.downloadPayslip.execute.mock.calls[0]?.[4].aborted).toBe(true);
    held.resolve(success(undefined));
    await pending;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      payslip: null,
      payments: null,
      download: "idle",
    });
    expect(listener).not.toHaveBeenCalled();
    await controller.refresh();
    await controller.download();
    expect(api.loadPayslip.execute).toHaveBeenCalledOnce();
  });
  it("does not publish an old directory response after deactivation", async () => {
    const api = actions(),
      held = gate<Awaited<ReturnType<PayrollUseCases["listPayslips"]["execute"]>>>();
    api.listPayslips.execute.mockReturnValueOnce(held.promise);
    const controller = new PayrollPayslipsController(api.listPayslips, access, search);
    owned.add(controller);
    controller.activate();
    controller.deactivate();
    expect(api.listPayslips.execute.mock.calls[0]?.[2].aborted).toBe(true);
    held.reject(new Error("late directory failure"));
    await flush();
    expect(controller.getSnapshot().page).toBeNull();
    controller.activate();
    await flush();
    expect(controller.getSnapshot().page?.items).toHaveLength(1);
  });
});
