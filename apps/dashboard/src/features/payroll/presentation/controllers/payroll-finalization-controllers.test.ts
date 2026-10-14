import { afterEach, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  finalizationId,
  payrollFinalizationDetails,
  payrollFinalizationPreparation,
} from "../../../../../tests/fixtures/payroll-finalizations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  toPayrollFinalizationDetails,
  toPayrollFinalizationPreparation,
} from "../../data/mappers/payroll-finalization-mapper";
import type { PayrollFinalizationDetails } from "../../domain/entities/payroll-finalization-details";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollFinalizationController } from "./payroll-finalization-controller";
import { PayrollFinalizationStartController } from "./payroll-finalization-start-controller";
import { PayrollFinalizationsController } from "./payroll-finalizations-controller";
import { PayrollPublicationsController } from "./payroll-publications-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.finalize"],
};
const owned = new Set<{ deactivate: () => void }>();
afterEach(() => {
  for (const controller of owned) controller.deactivate();
  owned.clear();
  vi.useRealTimers();
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
function details(status: PayrollFinalizationDetails["job"]["status"] = "QUEUED") {
  return toPayrollFinalizationDetails(
    payrollFinalizationDetails(status),
    access.companyId,
    finalizationId,
  );
}
function actions() {
  const preparation = toPayrollFinalizationPreparation(
    payrollFinalizationPreparation(),
    access.companyId,
    calculationId,
  );
  return {
    listPublicationRuns: {
      execute: vi
        .fn<PayrollUseCases["listPublicationRuns"]["execute"]>()
        .mockResolvedValue(success({ items: [preparation.run], nextCursor: null })),
    },
    loadFinalizations: {
      execute: vi
        .fn<PayrollUseCases["loadFinalizations"]["execute"]>()
        .mockResolvedValue(success({ preparation, finalizations: [] })),
    },
    loadFinalization: {
      execute: vi
        .fn<PayrollUseCases["loadFinalization"]["execute"]>()
        .mockResolvedValue(success(details())),
    },
    reviewFinalization: {
      execute: vi
        .fn<PayrollUseCases["reviewFinalization"]["execute"]>()
        .mockResolvedValue(success(preparation)),
    },
    startFinalization: {
      execute: vi
        .fn<PayrollUseCases["startFinalization"]["execute"]>()
        .mockImplementation(async (_a, _r, _o, input) => success({ id: input.id, version: 0 })),
    },
  };
}
function command() {
  const api = actions();
  let index = 0;
  const next = vi.fn(() => `ef900000-0000-4000-8000-${String(++index).padStart(12, "0")}`),
    controller = new PayrollFinalizationStartController(api, access, calculationId, next);
  owned.add(controller);
  return { api, controller, next };
}
describe("publication command ownership", () => {
  it("retains the original command through lost response, MFA and a later conflict", async () => {
    const { api, controller } = command(),
      held = gate<Result<MutationReceipt>>(),
      send = api.startFinalization.execute;
    send.mockReturnValueOnce(held.promise);
    controller.activate();
    await flush();
    const pending = controller.save("Publish September payroll");
    await controller.save("Duplicate click");
    expect(send).toHaveBeenCalledTimes(1);
    held.resolve(failed("connection_unavailable"));
    await pending;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    expect(api.reviewFinalization.execute).toHaveBeenCalledTimes(1);
    send.mockResolvedValueOnce(failed("mfa_required"));
    await controller.retry();
    expect(controller.getSnapshot().review).toBeNull();
    send.mockResolvedValueOnce(failed("payroll_already_finalized"));
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retry();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of send.mock.calls.slice(1))
      expect(call.slice(0, 4)).toEqual(send.mock.calls[0]?.slice(0, 4));
  });
  it.each(["acknowledgement", "rejection"])(
    "discards late %s after leaving the company",
    async (outcome) => {
      const { api, controller } = command(),
        held = gate<Result<MutationReceipt>>();
      api.startFinalization.execute.mockReturnValueOnce(held.promise);
      controller.activate();
      await flush();
      const pending = controller.save("Publish");
      controller.deactivate();
      expect(api.startFinalization.execute.mock.calls[0]?.[4].aborted).toBe(true);
      if (outcome === "acknowledgement") held.resolve(success({ id: finalizationId, version: 0 }));
      else held.reject(new Error("Late technical failure"));
      await pending;
      expect(controller.getSnapshot().stage).toBe("loading");
      expect(controller.getSnapshot().receipt).toBeNull();
    },
  );
  it("reloads approved versions only after a definite conflict and keeps the reason required", async () => {
    const { api, controller, next } = command();
    controller.activate();
    await flush();
    await controller.save("   ");
    expect(next).not.toHaveBeenCalled();
    api.startFinalization.execute.mockResolvedValueOnce(failed("approval_changed"));
    await controller.save("Publish");
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save("Changed");
    expect(api.startFinalization.execute).toHaveBeenCalledTimes(1);
    const raw = payrollFinalizationPreparation();
    raw.approvalVersion = 4;
    api.reviewFinalization.execute.mockResolvedValueOnce(
      success(toPayrollFinalizationPreparation(raw, access.companyId, calculationId)),
    );
    await controller.refresh();
    await controller.save("Publish reviewed payroll");
    expect(api.startFinalization.execute.mock.calls[1]?.[3].expectedApprovalVersion).toBe(4);
    expect(api.startFinalization.execute.mock.calls[1]?.[2]).not.toBe(
      api.startFinalization.execute.mock.calls[0]?.[2],
    );
  });
  it("does not allocate a command when publication is unavailable", async () => {
    const { api, controller, next } = command(),
      raw = payrollFinalizationPreparation();
    raw.availableActions = [];
    api.reviewFinalization.execute.mockResolvedValue(
      success(toPayrollFinalizationPreparation(raw, access.companyId, calculationId)),
    );
    controller.activate();
    await flush();
    await controller.save("Publish");
    expect(next).not.toHaveBeenCalled();
    expect(api.startFinalization.execute).not.toHaveBeenCalled();
  });
});
describe("publication read ownership", () => {
  it("clears directory and overview financial context when access is withdrawn", async () => {
    const api = actions(),
      directory = new PayrollPublicationsController(api.listPublicationRuns, access, {
        from: "2026-09",
        until: "2026-09",
        status: null,
        after: null,
      }),
      overview = new PayrollFinalizationsController(api.loadFinalizations, access, calculationId);
    owned.add(directory);
    owned.add(overview);
    directory.activate();
    overview.activate();
    await flush();
    expect(directory.getSnapshot().page?.items).toHaveLength(1);
    expect(overview.getSnapshot().data).not.toBeNull();
    api.listPublicationRuns.execute.mockResolvedValueOnce(failed("access_denied"));
    api.loadFinalizations.execute.mockResolvedValueOnce(failed("mfa_required"));
    await directory.refresh();
    await overview.refresh();
    expect(directory.getSnapshot().page).toBeNull();
    expect(overview.getSnapshot().data).toBeNull();
  });
  it("rejects an obsolete pending read after refresh and disposes its owned listener", async () => {
    const api = actions(),
      held = gate<Result<PayrollFinalizationDetails>>();
    api.loadFinalization.execute
      .mockReturnValueOnce(held.promise)
      .mockResolvedValueOnce(success(details("SUCCEEDED")));
    const controller = new PayrollFinalizationController(
      api.loadFinalization,
      access,
      finalizationId,
    );
    owned.add(controller);
    const notify = vi.fn(),
      unsubscribe = controller.subscribe(notify);
    controller.activate();
    await controller.refresh();
    expect(controller.getSnapshot().details?.job.status).toBe("SUCCEEDED");
    held.reject(new Error("Old transport failure"));
    await flush();
    expect(controller.getSnapshot().failure).toBeNull();
    unsubscribe();
    const calls = notify.mock.calls.length;
    controller.deactivate();
    expect(notify).toHaveBeenCalledTimes(calls);
  });
  it("limits automatic updates and retains no timer while hidden, complete or disposed", async () => {
    vi.useFakeTimers();
    const api = actions(),
      controller = new PayrollFinalizationController(api.loadFinalization, access, finalizationId);
    owned.add(controller);
    controller.setVisible(false);
    controller.activate();
    await flush();
    expect(api.loadFinalization.execute).not.toHaveBeenCalled();
    controller.setVisible(true);
    await flush();
    expect(api.loadFinalization.execute).toHaveBeenCalledTimes(1);
    for (let i = 0; i < 31; i++) await vi.advanceTimersByTimeAsync(4000);
    expect(api.loadFinalization.execute).toHaveBeenCalledTimes(31);
    expect(controller.getSnapshot().updatesPaused).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    controller.setVisible(false);
    await vi.advanceTimersByTimeAsync(100000);
    expect(api.loadFinalization.execute).toHaveBeenCalledTimes(31);
    controller.setVisible(true);
    api.loadFinalization.execute.mockResolvedValueOnce(success(details("SUCCEEDED")));
    await controller.refresh();
    expect(controller.getSnapshot().details?.job.status).toBe("SUCCEEDED");
    expect(vi.getTimerCount()).toBe(0);
    controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("aborts pending observation when hidden and drops late results", async () => {
    vi.useFakeTimers();
    const api = actions(),
      held = gate<Result<PayrollFinalizationDetails>>();
    api.loadFinalization.execute.mockReturnValueOnce(held.promise);
    const controller = new PayrollFinalizationController(
      api.loadFinalization,
      access,
      finalizationId,
    );
    owned.add(controller);
    controller.activate();
    controller.setVisible(false);
    expect(api.loadFinalization.execute.mock.calls[0]?.[2].aborted).toBe(true);
    held.resolve(success(details("SUCCEEDED")));
    await flush();
    expect(controller.getSnapshot().details).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    api.loadFinalization.execute.mockResolvedValueOnce(success(details("SUCCEEDED")));
    controller.setVisible(true);
    await flush();
    expect(controller.getSnapshot().details?.job.status).toBe("SUCCEEDED");
  });
  it("stops on transport failure, retaining authorized context until assurance is lost", async () => {
    vi.useFakeTimers();
    const api = actions(),
      controller = new PayrollFinalizationController(api.loadFinalization, access, finalizationId);
    owned.add(controller);
    controller.activate();
    await flush();
    api.loadFinalization.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await vi.advanceTimersByTimeAsync(4000);
    expect(controller.getSnapshot().details).not.toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    api.loadFinalization.execute.mockResolvedValueOnce(failed("mfa_required"));
    await controller.refresh();
    expect(controller.getSnapshot().details).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });
});
