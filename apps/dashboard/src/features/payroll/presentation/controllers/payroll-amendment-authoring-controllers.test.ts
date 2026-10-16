import { assert, describe, expect, it, vi } from "vitest";
import {
  amendmentPreparation,
  amendmentPreview,
} from "../../../../../tests/fixtures/payroll-amendment-authoring";
import { amendmentId, amendmentRevision } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import { payslipId } from "../../../../../tests/fixtures/payroll-payslips";
import { workAdjustmentId } from "../../../../../tests/fixtures/payroll-work";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentPreview } from "../../data/mappers/payroll-amendment-calculation-mapper";
import { toPayrollAmendment } from "../../data/mappers/payroll-amendment-mapper";
import { toPayrollAmendmentPreparation } from "../../data/mappers/payroll-amendment-preparation-mapper";
import type { PayrollAmendmentPreview } from "../../domain/entities/payroll-amendment-calculation";
import type {
  PayrollAmendmentCandidates,
  PayrollAmendmentEditorContext,
} from "../../domain/entities/payroll-amendment-preparation";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollAmendmentEditorController } from "./payroll-amendment-editor-controller";
import { PayrollAmendmentSourcesController } from "./payroll-amendment-sources-controller";

const access: CompanyAccess = {
  companyId: calculationCompany as CompanyId,
  permissions: ["payroll.amend"],
};
function pending<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture(edit = false, sourcesChanged = false) {
  const context: PayrollAmendmentEditorContext = {
    preparation: toPayrollAmendmentPreparation(
      amendmentPreparation(false, sourcesChanged ? 1 : 0),
      access.companyId,
      payslipId(),
    ),
    current: edit
      ? toPayrollAmendment(amendmentRevision(0), access.companyId, amendmentId())
      : null,
    sourcesChanged,
  };
  const actions = {
      loadAmendmentEditor: {
        execute: vi
          .fn<PayrollUseCases["loadAmendmentEditor"]["execute"]>()
          .mockResolvedValue(success(context)),
      },
      previewAmendment: {
        execute: vi
          .fn<PayrollUseCases["previewAmendment"]["execute"]>()
          .mockResolvedValue(success(toPayrollAmendmentPreview(amendmentPreview()))),
      },
      saveAmendment: {
        execute: vi
          .fn<PayrollUseCases["saveAmendment"]["execute"]>()
          .mockResolvedValue(success({ id: amendmentId(), version: edit ? 1 : 0 })),
      },
    },
    identifiers = vi.fn().mockReturnValueOnce(amendmentId()).mockReturnValueOnce(amendmentId(99));
  const controller = new PayrollAmendmentEditorController(
    actions,
    access,
    edit
      ? { kind: "edit", amendmentId: amendmentId() }
      : { kind: "create", assessmentId: payslipId() },
    identifiers,
  );
  return { context, actions, identifiers, controller };
}
async function start(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).not.toBe("loading"));
}
async function reviewed(f: ReturnType<typeof fixture>) {
  await start(f);
  f.controller.openCorrection(payslipId());
  const correction = amendmentRevision(0).terms.corrections[0];
  assert(correction);
  f.controller.applyCorrection(correction);
  await f.controller.preview();
}
describe("amendment editor ownership", () => {
  it("retains the selected attendance and operation through an uncertain save", async () => {
    const f = fixture();
    await start(f);
    f.controller.openCorrection(payslipId());
    f.controller.applyCorrection({
      assessmentId: payslipId(),
      compensation: null,
      input: null,
      workAdjustmentId: workAdjustmentId(),
    });
    await f.controller.preview();
    f.actions.saveAmendment.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await f.controller.save("Reviewed attendance");
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    const submitted = f.actions.saveAmendment.execute.mock.calls[0];
    f.controller.openCorrection(payslipId());
    f.controller.applyCorrection({
      assessmentId: payslipId(),
      compensation: null,
      input: null,
      workAdjustmentId: workAdjustmentId(2),
    });
    await f.controller.retrySave();
    expect(f.actions.saveAmendment.execute.mock.calls[1]?.slice(0, 4)).toEqual(
      submitted?.slice(0, 4),
    );
    expect(submitted?.[3].corrections[0]?.workAdjustmentId).toBe(workAdjustmentId());
    f.controller.deactivate();
  });
  it("treats stale attendance as a definite conflict requiring source reload", async () => {
    const f = fixture();
    await reviewed(f);
    f.actions.saveAmendment.execute.mockResolvedValueOnce(failed("stale_workforce_version"));
    await f.controller.save("Reviewed attendance");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.retrySave();
    expect(f.actions.saveAmendment.execute).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot().operationId).toBeNull();
    f.controller.deactivate();
  });
  it("owns one activation and ignores late reads after disposal", async () => {
    const f = fixture(),
      waiting = pending<Result<PayrollAmendmentEditorContext>>();
    f.actions.loadAmendmentEditor.execute.mockReturnValueOnce(waiting.promise);
    f.controller.activate();
    f.controller.activate();
    expect(f.actions.loadAmendmentEditor.execute).toHaveBeenCalledTimes(1);
    const signal = f.actions.loadAmendmentEditor.execute.mock.calls[0]?.[2];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    waiting.resolve(success(f.context));
    await Promise.resolve();
    expect(f.controller.getSnapshot().context).toBeNull();
    await f.controller.save("After disposal");
    await f.controller.preview();
    expect(f.actions.saveAmendment.execute).not.toHaveBeenCalled();
  });
  it("retains edited values and cancels a stale preview when another month edit starts", async () => {
    const f = fixture();
    await reviewed(f);
    const waiting = pending<Result<PayrollAmendmentPreview>>();
    f.actions.previewAmendment.execute.mockReturnValueOnce(waiting.promise);
    const preview = f.controller.preview();
    expect(f.controller.getSnapshot().stage).toBe("previewing");
    f.controller.openCorrection(payslipId());
    expect(f.actions.previewAmendment.execute.mock.calls[1]?.[3].aborted).toBe(true);
    const change = amendmentRevision(0).terms.corrections[0];
    assert(change?.compensation);
    change.compensation.basicSalary = "8500000";
    f.controller.applyCorrection(change);
    waiting.resolve(success(toPayrollAmendmentPreview(amendmentPreview())));
    await preview;
    expect(f.controller.getSnapshot().terms?.corrections[0]?.compensation?.basicSalary).toBe(
      "8500000",
    );
    expect(f.controller.getSnapshot().preview).toBeNull();
    await f.controller.save("Not reviewed");
    expect(f.actions.saveAmendment.execute).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("keeps the preview after inspection but invalidates it when removing a correction", async () => {
    const f = fixture();
    await reviewed(f);
    const preview = f.controller.getSnapshot().preview;
    f.controller.openCorrection(payslipId());
    f.controller.closeCorrection();
    expect(f.controller.getSnapshot().preview).toBe(preview);
    f.controller.openCorrection(payslipId());
    f.controller.removeCorrection();
    expect(f.controller.getSnapshot().preview).toBeNull();
    expect(f.controller.getSnapshot().terms?.corrections).toEqual([]);
    f.controller.deactivate();
  });
  it("requires explicit acknowledgement before replacing published source revisions", async () => {
    const f = fixture(true, true);
    await start(f);
    expect(f.controller.getSnapshot().stage).toBe("source-review");
    expect(f.controller.getSnapshot().terms?.sourceVersions[0]?.revision).toBe(0);
    await f.controller.preview();
    expect(f.actions.previewAmendment.execute).not.toHaveBeenCalled();
    f.controller.acceptCurrentSources();
    expect(f.controller.getSnapshot().terms?.sourceVersions[0]?.revision).toBe(1);
    expect(f.controller.getSnapshot().terms?.corrections).toEqual(
      f.context.current?.terms.corrections,
    );
    expect(f.controller.getSnapshot().dirty).toBe(true);
    f.controller.deactivate();
  });
  it("holds one immutable save through lost responses, late conflicts and denied retries", async () => {
    const f = fixture();
    await reviewed(f);
    f.actions.saveAmendment.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("stale_version"))
      .mockResolvedValueOnce(failed("access_denied"));
    await f.controller.save("Original reason");
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.refresh();
    await f.controller.preview();
    f.controller.openCorrection(payslipId());
    await f.controller.save("Changed reason");
    expect(f.actions.loadAmendmentEditor.execute).toHaveBeenCalledTimes(1);
    expect(f.actions.previewAmendment.execute).toHaveBeenCalledTimes(1);
    expect(f.identifiers).toHaveBeenCalledTimes(2);
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retrySave();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      context: null,
      terms: null,
      preview: null,
    });
    await f.controller.retrySave();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: amendmentId(), version: 0 },
    });
    const calls = f.actions.saveAmendment.execute.mock.calls;
    expect(calls).toHaveLength(4);
    for (const call of calls) expect(call.slice(0, 4)).toEqual(calls[0]?.slice(0, 4));
    expect(calls[0]?.[3].reason).toBe("Original reason");
    expect(Object.isFrozen(calls[0]?.[3].corrections[0]?.compensation)).toBe(true);
    f.controller.deactivate();
  });
  it("blocks duplicate saves and ignores a late committed result after disposal", async () => {
    const f = fixture();
    await reviewed(f);
    const waiting = pending<Result<{ id: string; version: number }>>();
    f.actions.saveAmendment.execute.mockReturnValueOnce(waiting.promise);
    const save = f.controller.save("One save");
    await f.controller.save("Duplicate");
    expect(f.actions.saveAmendment.execute).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot().stage).toBe("saving");
    f.controller.deactivate();
    expect(f.actions.saveAmendment.execute.mock.calls[0]?.[4].aborted).toBe(true);
    waiting.resolve(success({ id: amendmentId(), version: 0 }));
    await save;
    expect(f.controller.getSnapshot().receipt).toBeNull();
  });
  it.each([
    "stale_version",
    "payroll_amendment_sources_changed",
    "payroll_amendment_not_editable",
    "payroll_amendment_basis_changed",
    "payroll_amendment_sources_invalid",
  ])("an initial %s requires explicit reload", async (code) => {
    const f = fixture();
    await reviewed(f);
    f.actions.saveAmendment.execute.mockResolvedValueOnce(failed(code));
    await f.controller.save("Changed source");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "conflict",
      operationId: null,
      preview: null,
    });
    expect(f.controller.getSnapshot().terms).not.toBeNull();
    await f.controller.save("Blocked edit");
    expect(f.actions.saveAmendment.execute).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.actions.loadAmendmentEditor.execute).toHaveBeenCalledTimes(2);
    expect(f.controller.getSnapshot().stage).toBe("editing");
    f.controller.deactivate();
  });
  it("clears private preparation after a denied dependent preview", async () => {
    const f = fixture();
    await reviewed(f);
    f.actions.previewAmendment.execute.mockResolvedValueOnce(
      failed("payroll_amendment_self_preparation_forbidden"),
    );
    await f.controller.preview();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      context: null,
      terms: null,
      preview: null,
    });
    f.controller.deactivate();
  });
  it("replaces a pending read without accepting its late failure", async () => {
    const f = fixture(),
      waiting = pending<Result<PayrollAmendmentEditorContext>>();
    f.actions.loadAmendmentEditor.execute.mockReturnValueOnce(waiting.promise);
    f.controller.activate();
    await f.controller.refresh();
    waiting.resolve(failed("access_denied"));
    await Promise.resolve();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", failure: null });
    f.controller.deactivate();
  });
});
describe("source directory ownership", () => {
  it("cancels replacement and disposal while ignoring stale results", async () => {
    const waiting = pending<Result<PayrollAmendmentCandidates>>(),
      load = {
        execute: vi
          .fn<PayrollUseCases["listAmendmentSources"]["execute"]>()
          .mockResolvedValue(success({ items: [], nextCursor: null }))
          .mockReturnValueOnce(waiting.promise),
      };
    const controller = new PayrollAmendmentSourcesController(load, access, {
        year: 2026,
        query: "",
        employeeId: null,
        after: null,
      }),
      listener = vi.fn(),
      unsubscribe = controller.subscribe(listener);
    controller.activate();
    controller.activate();
    await controller.refresh();
    expect(load.execute).toHaveBeenCalledTimes(2);
    expect(load.execute.mock.calls[0]?.[2].aborted).toBe(true);
    waiting.resolve(failed("access_denied"));
    await Promise.resolve();
    expect(controller.getSnapshot()).toMatchObject({ stage: "ready", failure: null });
    unsubscribe();
    listener.mockClear();
    controller.deactivate();
    await controller.refresh();
    expect(listener).not.toHaveBeenCalled();
    expect(load.execute).toHaveBeenCalledTimes(2);
  });
});
