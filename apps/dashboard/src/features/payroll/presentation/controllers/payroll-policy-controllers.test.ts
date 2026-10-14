import { assert, describe, expect, it, vi } from "vitest";
import {
  payrollCompany,
  payrollPolicyPage,
  payrollPolicyRecord,
  payrollRule,
} from "../../../../../tests/fixtures/payroll-policies";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollPolicyReview } from "../../data/mappers/payroll-policy-mapper";
import type { PayrollPolicyReview } from "../../domain/entities/payroll-policy-review";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollPolicyController } from "./payroll-policy-controller";
import { PayrollPolicyEditorController } from "./payroll-policy-editor-controller";

const access: CompanyAccess = {
  companyId: payrollCompany as CompanyId,
  permissions: ["payroll.policy.manage"],
};
const review = (version = 23) =>
  toPayrollPolicyReview(
    payrollPolicyPage(payrollPolicyRecord(version)),
    access.companyId,
    "2026-06",
    null,
  );
const fields = { ...payrollPolicyRecord().current, reason: "Reviewed current policy" };
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function actions() {
  return {
    loadPolicy: {
      execute: vi
        .fn<PayrollUseCases["loadPolicy"]["execute"]>()
        .mockResolvedValue({ ok: true, value: review() }),
    },
    loadPolicyEditor: {
      execute: vi.fn<PayrollUseCases["loadPolicyEditor"]["execute"]>().mockResolvedValue({
        ok: true,
        value: { current: review().current, rules: [payrollRule] },
      }),
    },
    savePolicy: {
      execute: vi
        .fn<PayrollUseCases["savePolicy"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { id: payrollCompany, version: 24 } }),
    },
  };
}
describe("payroll policy ownership", () => {
  it("inspects acquired revisions without I/O and clears selection on refresh", async () => {
    const useCases = actions(),
      controller = new PayrollPolicyController(useCases.loadPolicy, access, "2026-06", null);
    controller.activate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    controller.selectRevision("0");
    expect(controller.getSnapshot().selected?.policy.appliedRevision).toBe(0);
    controller.inspectConfiguration();
    expect(controller.getSnapshot().selected?.policy.appliedRevision).toBe(23);
    expect(useCases.loadPolicy.execute).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot().selected).toBeNull();
    controller.deactivate();
  });
  it("rejects obsolete reads after replacement or disposal", async () => {
    const useCases = actions(),
      first = deferred<Result<PayrollPolicyReview>>(),
      second = deferred<Result<PayrollPolicyReview>>();
    useCases.loadPolicy.execute
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const controller = new PayrollPolicyController(useCases.loadPolicy, access, "2026-06", null);
    controller.activate();
    const oldSignal = useCases.loadPolicy.execute.mock.calls[0]?.[3];
    const refreshed = controller.refresh();
    expect(oldSignal?.aborted).toBe(true);
    second.resolve({ ok: true, value: review(24) });
    await refreshed;
    first.resolve({ ok: true, value: review(23) });
    await Promise.resolve();
    expect(controller.getSnapshot().review?.current?.version).toBe(24);
    const late = deferred<Result<PayrollPolicyReview>>();
    useCases.loadPolicy.execute.mockReturnValue(late.promise);
    const pending = controller.refresh();
    controller.deactivate();
    late.resolve(failed("access_denied"));
    await pending;
    expect(controller.getSnapshot().review).toBeNull();
    expect(controller.getSnapshot().failure).toBeNull();
  });
  it("freezes one operation through response loss, duplicate clicks and rejected retries", async () => {
    const useCases = actions(),
      gate = deferred<Result<MutationReceipt>>();
    useCases.savePolicy.execute
      .mockReturnValueOnce(gate.promise)
      .mockResolvedValueOnce(failed("stale_version"));
    const identifiers = vi.fn(() => "c1000000-0000-4000-8000-000000000001");
    const controller = new PayrollPolicyEditorController(useCases, access, "2026-06", identifiers);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const input = { ...fields, reviewReferences: [...fields.reviewReferences] },
      pending = controller.save(input);
    input.reviewReferences[0] = "https://example.test/changed-after-submission";
    await controller.save(input);
    expect(useCases.savePolicy.execute).toHaveBeenCalledTimes(1);
    gate.resolve(failed("connection_unavailable"));
    await pending;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    expect(useCases.loadPolicyEditor.execute).toHaveBeenCalledTimes(1);
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    const original = useCases.savePolicy.execute.mock.calls[0];
    assert(original, "Expected original save request");
    expect(original[2]).toMatchObject({
      expectedVersion: 23,
      reviewReferences: fields.reviewReferences,
    });
    for (const call of useCases.savePolicy.execute.mock.calls)
      expect(call.slice(0, 3)).toEqual(original.slice(0, 3));
    expect(identifiers).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });
  it("requires a current review after a definite conflict and uses a new observed version", async () => {
    const useCases = actions();
    useCases.savePolicy.execute.mockResolvedValueOnce(failed("stale_version"));
    let next = 0;
    const controller = new PayrollPolicyEditorController(
      useCases,
      access,
      "2026-06",
      () => `c1000000-0000-4000-8000-${String(++next).padStart(12, "0")}`,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields);
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(fields);
    expect(useCases.savePolicy.execute).toHaveBeenCalledTimes(1);
    useCases.loadPolicyEditor.execute.mockResolvedValue({
      ok: true,
      value: { current: review(24).current, rules: [payrollRule] },
    });
    await controller.refresh();
    await controller.save(fields);
    expect(useCases.savePolicy.execute.mock.calls.map((call) => call[2].expectedVersion)).toEqual([
      23, 24,
    ]);
    expect(useCases.savePolicy.execute.mock.calls[0]?.[1]).not.toEqual(
      useCases.savePolicy.execute.mock.calls[1]?.[1],
    );
    controller.deactivate();
  });
  it("hides revoked policy data without making an unknown command editable", async () => {
    const useCases = actions();
    useCases.savePolicy.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("access_denied"));
    const controller = new PayrollPolicyEditorController(
      useCases,
      access,
      "2026-06",
      () => "c1000000-0000-4000-8000-000000000001",
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields);
    await controller.retrySave();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", context: null });
    await controller.save(fields);
    await controller.refresh();
    expect(useCases.savePolicy.execute).toHaveBeenCalledTimes(2);
    expect(useCases.loadPolicyEditor.execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });
  it("retains editing context for MFA recovery and cancels late save results on disposal", async () => {
    const useCases = actions();
    useCases.savePolicy.execute.mockResolvedValueOnce(failed("recent_authentication_required"));
    const controller = new PayrollPolicyEditorController(
      useCases,
      access,
      "2026-06",
      () => "c1000000-0000-4000-8000-000000000001",
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const context = controller.getSnapshot().context;
    await controller.save(fields);
    expect(controller.getSnapshot().stage).toBe("editing");
    expect(controller.getSnapshot().context).toBe(context);
    const gate = deferred<Result<MutationReceipt>>();
    useCases.savePolicy.execute.mockReturnValueOnce(gate.promise);
    const pending = controller.save(fields),
      signal = useCases.savePolicy.execute.mock.calls.at(-1)?.[3];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    gate.resolve({ ok: true, value: { id: payrollCompany, version: 24 } });
    await pending;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(controller.getSnapshot().context).toBeNull();
  });
});
