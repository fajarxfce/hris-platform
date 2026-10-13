import { afterEach, assert, describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  toLeaveEvidenceOptions,
  toLeavePreparationDirectory,
} from "../../data/mappers/leave-preparation-mapper";
import { toLeaveRequestTypes } from "../../data/mappers/leave-request-type-mapper";
import {
  preparationAccess as access,
  preparationCompany as company,
  preparationDirectoryDto,
  preparationEvidenceDto,
  preparationReview,
  preparationTypesDto,
  preparationSelection as selection,
} from "../../di/leave-preparation-fixture";
import type { ReviewedLeaveSelection } from "../../domain/entities/leave-request-preview";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveCreationController } from "./leave-creation-controller";

function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
const controllers: LeaveCreationController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});

function fixture(active = true, permissions = ["leave.manage"]) {
  const preview = vi
    .fn<LeaveUseCases["previewRequest"]["execute"]>()
    .mockResolvedValue(success(preparationReview()));
  const submit = vi
    .fn<LeaveUseCases["submitRequest"]["execute"]>()
    .mockImplementation(async (_access, _operation, command) =>
      success({ id: command.id, version: 0 }),
    );
  let sequence = 0;
  const next = vi.fn(() => `90000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new LeaveCreationController(
    { previewRequest: { execute: preview }, submitRequest: { execute: submit } },
    { ...access, permissions },
    next,
  );
  controllers.push(controller);
  if (active) controller.activate();
  const employee = toLeavePreparationDirectory(preparationDirectoryDto(), company, null).employees
    .items[0];
  assert(employee);
  const type = toLeaveRequestTypes(preparationTypesDto(), company, "2026-10-05", null).items[0];
  assert(type);
  const evidence = toLeaveEvidenceOptions(preparationEvidenceDto(), company, employee.id, null)
    .items[0];
  assert(evidence);
  const choose = () => {
    controller.selectEmployee(employee);
    controller.selectType(type, type.asOf);
  };
  return { controller, preview, submit, next, employee, type, evidence, choose };
}

describe("leave creation ownership", () => {
  it("requires an active route, management access and explicit owned selections", async () => {
    const f = fixture(false);
    f.choose();
    await f.controller.preview(selection.days);
    await f.controller.confirm("Personal leave");
    expect(f.preview).not.toHaveBeenCalled();
    f.controller.activate();
    await f.controller.preview(selection.days);
    expect(f.preview).not.toHaveBeenCalled();
    f.controller.selectEmployee({ ...f.employee, companyId: "other" as CompanyId });
    expect(f.controller.getSnapshot().employee).toBeNull();
    f.choose();
    await f.controller.preview(selection.days);
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    const denied = fixture(true, ["leave.read"]);
    denied.choose();
    await denied.controller.preview(selection.days);
    expect(denied.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      employee: null,
      failure: { code: "access_denied" },
    });
    expect(denied.preview).not.toHaveBeenCalled();
  });

  it.each(["success", "failure"])(
    "date changes abort a pending preview and reject its late %s",
    async (outcome) => {
      const f = fixture(),
        held = deferred<Result<ReviewedLeaveSelection>>();
      f.choose();
      f.preview.mockReturnValueOnce(held.promise);
      const pending = f.controller.preview(selection.days);
      await f.controller.preview(selection.days);
      expect(f.preview).toHaveBeenCalledOnce();
      const oldSignal = f.preview.mock.lastCall?.[2];
      f.controller.changeDates("2026-10-07");
      expect(oldSignal?.aborted).toBe(true);
      if (outcome === "success") held.resolve(success(preparationReview()));
      else held.reject(new Error("Late private error"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        type: null,
        review: null,
        failure: null,
      });
      f.controller.selectType(f.type, "2026-10-07");
      expect(f.controller.getSnapshot().type).toBeNull();
    },
  );

  it("employee replacement clears selected evidence and cancels the previous review", async () => {
    const f = fixture(),
      held = deferred<Result<ReviewedLeaveSelection>>();
    f.choose();
    f.controller.addEvidence(f.evidence);
    f.preview.mockReturnValueOnce(held.promise);
    const pending = f.controller.preview(selection.days);
    f.controller.selectEmployee({
      ...f.employee,
      id: "b2000000-0000-4000-8000-000000000002",
      legalName: "Other employee",
    });
    held.resolve(success(preparationReview()));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      evidence: [],
      review: null,
      employee: { legalName: "Other employee" },
    });
    f.controller.addEvidence(f.evidence);
    expect(f.controller.getSnapshot().evidence).toHaveLength(0);
  });

  it("freezes one submission and retries its original body through response loss", async () => {
    const f = fixture(),
      held = deferred<Result<MutationReceipt>>();
    f.choose();
    f.controller.addEvidence(f.evidence);
    await f.controller.preview(selection.days);
    f.submit.mockReturnValueOnce(held.promise);
    const pending = f.controller.confirm("Original reason");
    const original = f.submit.mock.calls[0];
    expect(Object.isFrozen(original?.[2])).toBe(true);
    expect(Object.isFrozen(original?.[2].days[0])).toBe(true);
    await f.controller.confirm("Replacement");
    f.controller.changeDates("2026-10-10");
    f.controller.selectEmployee({ ...f.employee, legalName: "Replacement" });
    f.controller.removeEvidence(f.evidence.revisionId);
    await f.controller.preview(selection.days);
    await f.controller.retry();
    expect(f.preview).toHaveBeenCalledOnce();
    expect(f.submit).toHaveBeenCalledOnce();
    expect(f.controller.getSnapshot().evidence).toHaveLength(1);
    held.resolve(failed("request_timeout"));
    await pending;
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    expect(f.submit.mock.calls[1]?.slice(0, 3)).toEqual(original?.slice(0, 3));
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      employee: null,
      review: null,
      evidence: [],
      receipt: { id: original?.[2].id },
    });
  });

  it("retains an uncertain command through denied retries while removing private review data", async () => {
    const f = fixture();
    f.choose();
    f.controller.addEvidence(f.evidence);
    await f.controller.preview(selection.days);
    f.submit
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("leave_preview_changed"));
    await f.controller.confirm("Original");
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      employee: null,
      type: null,
      evidence: [],
      review: null,
    });
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    const held = deferred<Result<MutationReceipt>>();
    f.submit.mockReturnValueOnce(held.promise);
    const pending = f.controller.retry();
    f.choose();
    await f.controller.preview(selection.days);
    expect(f.preview).toHaveBeenCalledOnce();
    held.resolve(success({ id: f.submit.mock.calls[0]?.[2].id ?? "", version: 0 }));
    await pending;
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.submit.mock.calls)
      expect(call.slice(0, 3)).toEqual(f.submit.mock.calls[0]?.slice(0, 3));
  });

  it("a definite preview conflict requires a new review without discarding the selected employee", async () => {
    const f = fixture();
    f.choose();
    await f.controller.preview(selection.days);
    f.submit.mockResolvedValueOnce(failed("leave_preview_changed"));
    await f.controller.confirm("Personal leave");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      review: null,
      operationId: null,
      employee: f.employee,
    });
    await f.controller.confirm("Must not submit stale review");
    expect(f.submit).toHaveBeenCalledOnce();
    await f.controller.preview(selection.days);
    await f.controller.confirm("Personal leave");
    expect(f.submit.mock.calls[1]?.[1]).not.toBe(f.submit.mock.calls[0]?.[1]);
    expect(f.controller.getSnapshot().stage).toBe("saved");
  });

  it("keeps a read denial gated until a new admitted employee selection and preview", async () => {
    const f = fixture();
    f.choose();
    await f.controller.preview(selection.days);
    f.controller.withdrawAccess({ code: "mfa_required", fields: {}, parameters: {} });
    f.controller.changeDates("2026-10-05");
    f.controller.selectType(f.type, f.type.asOf);
    await f.controller.preview(selection.days);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      employee: null,
      review: null,
    });
    expect(f.preview).toHaveBeenCalledOnce();
    f.choose();
    await f.controller.preview(selection.days);
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
  });

  it.each(["success", "failure"])(
    "disposal cancels submission and ignores a late %s after reactivation",
    async (outcome) => {
      const f = fixture(),
        held = deferred<Result<MutationReceipt>>();
      f.choose();
      await f.controller.preview(selection.days);
      f.submit.mockReturnValueOnce(held.promise);
      const pending = f.controller.confirm("Personal leave"),
        signal = f.submit.mock.lastCall?.[3];
      f.controller.deactivate();
      expect(signal?.aborted).toBe(true);
      f.controller.activate();
      f.choose();
      await f.controller.preview(selection.days);
      if (outcome === "success") held.resolve(success({ id: "late", version: 0 }));
      else held.reject(new Error("Late private result"));
      await pending;
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "reviewing",
        receipt: null,
        failure: null,
      });
    },
  );
});
