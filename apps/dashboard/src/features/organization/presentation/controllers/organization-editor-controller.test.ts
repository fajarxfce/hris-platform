import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { OrganizationUnitId } from "../../domain/entities/organization-unit";
import type { OrganizationUnitDetails } from "../../domain/entities/organization-unit-details";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import {
  type OrganizationEditableFields,
  OrganizationEditorController,
} from "./organization-editor-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "50000000-0000-4000-8000-000000000001" as OrganizationUnitId;
const access = { companyId, permissions: ["company.read", "company.manage"] };
const details: OrganizationUnitDetails = {
  companyId,
  parent: null,
  unit: {
    companyId,
    id,
    code: "SALES",
    name: "Sales",
    kind: "DEPARTMENT",
    parentId: null,
    timezone: null,
    active: true,
    version: 7,
  },
};
const fields: OrganizationEditableFields = {
  code: "SALES",
  name: "Sales North",
  kind: "DEPARTMENT",
  parentId: null,
  timezone: null,
  active: true,
};
const owners: OrganizationEditorController[] = [];
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
});
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture(creating = false, permissions = access.permissions) {
  const loadUnit = {
    execute: vi
      .fn<OrganizationUseCases["loadUnit"]["execute"]>()
      .mockResolvedValue(success(details)),
  };
  const saveUnit = {
    execute: vi
      .fn<OrganizationUseCases["saveUnit"]["execute"]>()
      .mockResolvedValue(success({ id, version: creating ? 0 : 8 })),
  };
  let issued = 0;
  const next = vi.fn(
    () => `60000000-0000-4000-8000-${String(++issued).padStart(12, "0")}` as OperationId,
  );
  const controller = new OrganizationEditorController(
    { loadUnit, saveUnit },
    { ...access, permissions },
    creating,
    id,
    next,
  );
  owners.push(controller);
  controller.activate();
  return { controller, loadUnit, saveUnit, next };
}
describe("organization submission ownership", () => {
  it("pins a flat immutable payload and observed version, rejects double submit, and accepts one receipt", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const response = pending<Result<MutationReceipt>>();
    f.saveUnit.execute.mockReturnValueOnce(response.promise);
    const input = { ...fields };
    const saving = f.controller.save(input);
    input.name = "Changed after submission";
    await f.controller.save(fields);
    await f.controller.refresh();
    expect(f.saveUnit.execute).toHaveBeenCalledOnce();
    expect(f.saveUnit.execute.mock.lastCall?.[2]).toEqual({ ...fields, id, expectedVersion: 7 });
    expect(Object.isFrozen(f.saveUnit.execute.mock.lastCall?.[2])).toBe(true);
    response.resolve(success({ id, version: 8 }));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      operationId: null,
      receipt: { id, version: 8 },
    });
    expect(f.loadUnit.execute).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
  });
  it("keeps uncertain outcomes pinned through MFA and conflict rejections until explicit replay succeeds", async () => {
    const f = fixture(true);
    f.saveUnit.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    const first = f.saveUnit.execute.mock.calls[0];
    await f.controller.save({ ...fields, name: "A new change" });
    await f.controller.refresh();
    expect(f.saveUnit.execute).toHaveBeenCalledOnce();
    for (let attempt = 0; attempt < 2; attempt += 1) {
      await f.controller.retrySave();
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "unconfirmed",
        operationId: first?.[1],
      });
      expect(f.saveUnit.execute.mock.lastCall?.[1]).toBe(first?.[1]);
      expect(f.saveUnit.execute.mock.lastCall?.[2]).toBe(first?.[2]);
    }
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.loadUnit.execute).not.toHaveBeenCalled();
  });
  it("permits a corrected first rejection with a new ID, but a version conflict needs an explicit fresh read", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    f.saveUnit.execute
      .mockResolvedValueOnce(failed("parent_unavailable"))
      .mockResolvedValueOnce(failed("stale_version"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    await f.controller.save({ ...fields, parentId: id });
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields);
    expect(f.saveUnit.execute).toHaveBeenCalledTimes(2);
    f.loadUnit.execute.mockResolvedValueOnce(
      success({ ...details, unit: { ...details.unit, version: 8 } }),
    );
    await f.controller.refresh();
    f.saveUnit.execute.mockResolvedValueOnce(success({ id, version: 9 }));
    await f.controller.save(fields);
    expect(f.saveUnit.execute.mock.lastCall?.[2].expectedVersion).toBe(8);
    expect(new Set(f.saveUnit.execute.mock.calls.map((call) => call[1])).size).toBe(3);
  });
  it.each([false, true])(
    "cancels a pending write on disposal and ignores its late completion (failure: %s)",
    async (rejects) => {
      const f = fixture(true);
      const response = pending<Result<MutationReceipt>>();
      f.saveUnit.execute.mockReturnValueOnce(response.promise);
      const saving = f.controller.save(fields);
      const signal = f.saveUnit.execute.mock.lastCall?.[3];
      f.controller.deactivate();
      f.controller.activate();
      if (rejects) response.reject(new Error("PRIVATE LATE ERROR"));
      else response.resolve(success({ id, version: 0 }));
      await saving;
      expect(signal?.aborted).toBe(true);
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "editing",
        receipt: null,
        operationId: null,
        failure: null,
      });
    },
  );
  it("does not admit management without read access, and treats an unexpected write exception as uncertain", async () => {
    const forbidden = fixture(true, ["company.manage"]);
    await forbidden.controller.save(fields);
    expect(forbidden.controller.getSnapshot().failure?.code).toBe("access_denied");
    expect(forbidden.saveUnit.execute).not.toHaveBeenCalled();
    const f = fixture(true);
    f.saveUnit.execute.mockRejectedValueOnce(new Error("PRIVATE FAILURE"));
    await f.controller.save(fields);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
  });
});
