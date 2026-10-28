import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { SavedReportFilter } from "../../domain/entities/saved-report-filter";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ReportFilterEditorController } from "./report-filter-editor-controller";

const companyId = "00000000-0000-4000-8000-000000000001" as CompanyId;
const id = "00000000-0000-4000-8000-000000000002";
const operation = "00000000-0000-4000-8000-000000000003";
const access = { companyId, permissions: ["reports.read", "people.read"] };
const filter: SavedReportFilter = {
  id,
  companyId,
  name: "My headcount",
  kind: "HEADCOUNT",
  definitionVersion: "headcount.v1",
  parameters: { asOf: "2026-10-01", from: null, until: null, status: null, companies: [companyId] },
  version: 2,
  createdAt: "2026-10-01T00:00:00Z",
  updatedAt: "2026-10-01T00:00:00Z",
};
function pending<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { resolve, promise };
}
function fixture(creating = false) {
  const actions = {
    getSavedFilter: {
      execute: vi
        .fn<ReportingUseCases["getSavedFilter"]["execute"]>()
        .mockResolvedValue(success(filter)),
    },
    saveFilter: {
      execute: vi
        .fn<ReportingUseCases["saveFilter"]["execute"]>()
        .mockResolvedValue(success({ id, version: 3 })),
    },
    deleteFilter: {
      execute: vi
        .fn<ReportingUseCases["deleteFilter"]["execute"]>()
        .mockResolvedValue(success({ id, version: 3 })),
    },
  };
  const next = vi.fn(() => operation);
  const controller = new ReportFilterEditorController(actions, access, creating, id, next);
  return { actions, next, controller };
}
async function activate(controller: ReportFilterEditorController) {
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
}

describe("saved filter command ownership", () => {
  it.each(["save", "delete"] as const)(
    "retains one %s command through response loss, MFA denial and explicit recovery",
    async (kind) => {
      const { actions, next, controller } = fixture();
      const call = kind === "save" ? actions.saveFilter.execute : actions.deleteFilter.execute;
      const first = pending<Result<MutationReceipt>>();
      call.mockReturnValueOnce(first.promise).mockResolvedValueOnce(failed("mfa_required"));
      await activate(controller);
      const companies = [companyId];
      if (kind === "delete") controller.reviewDelete();
      const saving =
        kind === "save"
          ? controller.save({ ...filter, parameters: { ...filter.parameters, companies } })
          : controller.confirmDelete();
      companies.push("00000000-0000-4000-8000-000000000004" as CompanyId);
      expect(controller.getSnapshot().stage).toBe("saving");
      await controller.save(filter);
      await controller.retry();
      await controller.refresh();
      controller.reviewDelete();
      await controller.confirmDelete();
      expect(call).toHaveBeenCalledTimes(1);
      first.resolve(failed("connection_unavailable"));
      await saving;
      expect(controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: operation,
        command: kind,
      });
      await controller.refresh();
      await controller.save(filter);
      controller.reviewDelete();
      await controller.retry();
      expect(controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        failure: { code: "mfa_required" },
      });
      await controller.retry();
      expect(controller.getSnapshot()).toMatchObject({
        stage: "completed",
        command: kind,
        receipt: { id, version: 3 },
        filter: null,
        operationId: null,
      });
      expect(call).toHaveBeenCalledTimes(3);
      expect(next).toHaveBeenCalledTimes(1);
      for (const [, key, input] of call.mock.calls) {
        expect(key).toBe(operation);
        expect(input).toMatchObject({ id, expectedVersion: 2 });
        expect(Object.isFrozen(input)).toBe(true);
        if ("parameters" in input) expect(input.parameters.companies).toEqual([companyId]);
      }
      controller.deactivate();
    },
  );

  it("requires a reviewed current version to delete and abandons a dismissed confirmation", async () => {
    const { actions, controller } = fixture();
    await controller.confirmDelete();
    expect(actions.deleteFilter.execute).not.toHaveBeenCalled();
    await activate(controller);
    await controller.confirmDelete();
    expect(actions.deleteFilter.execute).not.toHaveBeenCalled();
    controller.reviewDelete();
    controller.cancelDelete();
    await controller.confirmDelete();
    expect(actions.deleteFilter.execute).not.toHaveBeenCalled();
    controller.reviewDelete();
    await controller.confirmDelete();
    expect(actions.deleteFilter.execute.mock.lastCall?.[2]).toEqual({ id, expectedVersion: 2 });
    controller.deactivate();
    const creating = fixture(true);
    await activate(creating.controller);
    creating.controller.reviewDelete();
    await creating.controller.confirmDelete();
    expect(creating.actions.deleteFilter.execute).not.toHaveBeenCalled();
    await creating.controller.save(filter);
    expect(creating.actions.saveFilter.execute.mock.lastCall?.[2].expectedVersion).toBeNull();
    creating.controller.deactivate();
  });

  it.each([
    "stale_version",
    "report_filter_not_found",
    "invalid_report_filter_name",
    "mfa_required",
    "report_filter_limit",
  ])("allows a new reviewed command after a definitive first %s rejection", async (code) => {
    const { actions, controller } = fixture();
    actions.saveFilter.execute.mockResolvedValueOnce(failed(code));
    await activate(controller);
    await controller.save(filter);
    expect(controller.getSnapshot().stage).toBe(
      ["stale_version", "report_filter_not_found"].includes(code) ? "conflict" : "editing",
    );
    expect(controller.getSnapshot().operationId).toBeNull();
    await controller.retry();
    expect(actions.saveFilter.execute).toHaveBeenCalledTimes(1);
    actions.getSavedFilter.execute.mockResolvedValueOnce(success({ ...filter, version: 5 }));
    await controller.refresh();
    await controller.save(filter);
    expect(actions.saveFilter.execute.mock.lastCall?.[2].expectedVersion).toBe(5);
    controller.deactivate();
  });

  it("cancels obsolete reads and writes, ignores late responses and clears personal state on disposal", async () => {
    const { actions, controller } = fixture();
    const read = pending<Result<SavedReportFilter>>();
    actions.getSavedFilter.execute.mockReturnValueOnce(read.promise);
    controller.activate();
    const readSignal = actions.getSavedFilter.execute.mock.calls[0]?.[2];
    await controller.refresh();
    expect(readSignal?.aborted).toBe(true);
    read.resolve(success({ ...filter, name: "Obsolete" }));
    await read.promise;
    expect(controller.getSnapshot().filter?.name).toBe(filter.name);
    const write = pending<Result<MutationReceipt>>();
    actions.saveFilter.execute.mockReturnValueOnce(write.promise);
    const saving = controller.save(filter);
    const writeSignal = actions.saveFilter.execute.mock.calls[0]?.[3];
    const listener = vi.fn();
    const stop = controller.subscribe(listener);
    stop();
    controller.deactivate();
    expect(writeSignal?.aborted).toBe(true);
    expect(listener).not.toHaveBeenCalled();
    expect(controller.getSnapshot().filter).toBeNull();
    expect(controller.getSnapshot().operationId).toBeNull();
    await activate(controller);
    write.resolve(success({ id, version: 3 }));
    await saving;
    expect(controller.getSnapshot()).toMatchObject({ stage: "editing", filter });
    await controller.retry();
    expect(actions.saveFilter.execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });

  it("retains thrown commands without an implicit retry and clears a denied read", async () => {
    const { actions, controller } = fixture();
    await activate(controller);
    actions.saveFilter.execute.mockImplementationOnce(() => {
      throw new Error("PRIVATE");
    });
    await controller.save(filter);
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
    expect(actions.saveFilter.execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
    actions.getSavedFilter.execute.mockResolvedValueOnce(failed("access_denied"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(controller.getSnapshot().filter).toBeNull();
    controller.deactivate();
  });
});
