import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toAudienceGroup } from "../../data/mappers/audience-group-mapper";
import { employeeId, groupDetail, groupId } from "../../di/audience-group-fixture";
import { access } from "../../di/communications-fixture";
import type { AudienceGroup, AudienceGroupId } from "../../domain/entities/audience-group";
import {
  AudienceGroupEditorController,
  type AudienceGroupFields,
} from "./audience-group-editor-controller";

const operation = "11000000-0000-4000-8000-000000000009";
const fields: AudienceGroupFields = {
  name: "Field team",
  active: true,
  employmentIds: [],
  reason: "Review",
};
const record = (version: number) =>
  toAudienceGroup(groupDetail(version), groupId as AudienceGroupId, null);
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}
describe("audience group command ownership", () => {
  it("keeps the original members, version and operation through response loss and later rejection", async () => {
    const pending = deferred<Result<MutationReceipt>>();
    const execute = vi
      .fn()
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(failed("stale_version"))
      .mockResolvedValueOnce(success({ id: groupId, version: 0 }));
    const next = vi.fn(() => operation);
    const controller = new AudienceGroupEditorController(
      { loadGroup: { execute: vi.fn() }, saveGroup: { execute } },
      access,
      true,
      groupId,
      next,
    );
    controller.activate();
    const members = [employeeId(1)];
    const saving = controller.save({ ...fields, employmentIds: members });
    await controller.save(fields);
    expect(next).toHaveBeenCalledTimes(1);
    members.length = 0;
    pending.resolve(failed("request_timeout"));
    await saving;
    const original = execute.mock.calls[0];
    expect(original?.[2]).toMatchObject({ employmentIds: [employeeId(1)], expectedVersion: null });
    expect(Object.isFrozen(original?.[2].employmentIds)).toBe(true);
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    await controller.save(fields);
    expect(execute).toHaveBeenCalledTimes(1);
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retrySave();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "saved",
      receipt: { id: groupId, version: 0 },
      operationId: null,
    });
    expect(execute.mock.calls[2]?.[2]).toBe(original?.[2]);
    expect(execute.mock.calls[2]?.[1]).toBe(original?.[1]);
    controller.deactivate();
  });
  it("requires explicit review after a definite version conflict", async () => {
    const load = vi
      .fn()
      .mockResolvedValueOnce(success(record(4)))
      .mockResolvedValueOnce(success({ ...record(6), active: false }));
    const save = vi.fn().mockResolvedValue(failed("stale_version"));
    const controller = new AudienceGroupEditorController(
      { loadGroup: { execute: load }, saveGroup: { execute: save } },
      access,
      false,
      groupId,
      () => operation,
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields);
    expect(save.mock.calls[0]?.[2]).toMatchObject({ expectedVersion: 4 });
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(fields);
    expect(save).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "editing",
      group: { version: 6, active: false },
    });
    controller.deactivate();
  });
  it("discards replaced reads, late failures and disposed save receipts", async () => {
    const old = deferred<Result<AudienceGroup>>();
    const fresh = deferred<Result<AudienceGroup>>();
    const load = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise);
    const command = deferred<Result<MutationReceipt>>();
    const save = vi.fn().mockReturnValue(command.promise);
    const controller = new AudienceGroupEditorController(
      { loadGroup: { execute: load }, saveGroup: { execute: save } },
      access,
      false,
      groupId,
      () => operation,
    );
    controller.activate();
    const refreshing = controller.refresh();
    const oldSignal = load.mock.calls[0]?.[3] as AbortSignal;
    expect(oldSignal.aborted).toBe(true);
    fresh.resolve(success(record(2)));
    await refreshing;
    old.resolve(failed("company_access_denied"));
    await old.promise;
    expect(controller.getSnapshot()).toMatchObject({ stage: "editing", group: { version: 2 } });
    const saving = controller.save(fields);
    controller.deactivate();
    const saveSignal = save.mock.calls[0]?.[3] as AbortSignal;
    expect(saveSignal.aborted).toBe(true);
    command.resolve(success({ id: groupId, version: 3 }));
    await saving;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      group: null,
      receipt: null,
      operationId: null,
    });
  });
  it("allows correcting a known first validation rejection without reusing the old operation", async () => {
    const save = vi.fn().mockResolvedValue(failed("audience_group_employee_unavailable"));
    const next = vi.fn().mockReturnValueOnce(operation).mockReturnValueOnce(employeeId(8));
    const controller = new AudienceGroupEditorController(
      { loadGroup: { execute: vi.fn() }, saveGroup: { execute: save } },
      access,
      true,
      groupId,
      next,
    );
    controller.activate();
    await controller.save(fields);
    expect(controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    await controller.retrySave();
    expect(save).toHaveBeenCalledTimes(1);
    await controller.save(fields);
    expect(save.mock.calls[0]?.[1]).not.toBe(save.mock.calls[1]?.[1]);
    controller.deactivate();
  });
});
