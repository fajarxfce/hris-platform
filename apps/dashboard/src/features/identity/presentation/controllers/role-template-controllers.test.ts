import { expect, it, vi } from "vitest";
import {
  roleCatalog,
  roleTemplateId,
  roleTemplateReview,
} from "../../../../../tests/fixtures/role-templates";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { RoleTemplateId, RoleTemplatePage } from "../../domain/entities/role-template";
import type { RoleTemplateReview } from "../../domain/entities/role-template-review";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { RoleTemplateController } from "./role-template-controller";
import { RoleTemplateEditorController } from "./role-template-editor-controller";
import { RoleTemplatesController } from "./role-templates-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["identity.manage"],
};
const raw = roleTemplateReview();
const review: RoleTemplateReview = {
  ...raw,
  current: { ...raw.current, id: raw.current.id as RoleTemplateId, companyId: access.companyId },
};
const fields = {
  code: "CHANGED_CODE",
  name: "New reviewer name",
  active: false,
  permissions: ["company.read", "leave.read"],
  reason: "Reviewed responsibilities",
};
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture(creating = false) {
  const actions = {
    loadRoleTemplate: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadRoleTemplate"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    loadAssignablePermissions: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadAssignablePermissions"]["execute"]>()
        .mockResolvedValue(success(roleCatalog)),
    },
    saveRoleTemplate: {
      execute: vi
        .fn<IdentityAdministrationUseCases["saveRoleTemplate"]["execute"]>()
        .mockResolvedValue(success({ id: roleTemplateId(), version: creating ? 0 : 2 })),
    },
  };
  const identifier = vi.fn(() => "d2000000-0000-4000-8000-000000000001");
  const controller = new RoleTemplateEditorController(
    actions,
    access,
    creating,
    roleTemplateId(),
    identifier,
  );
  return { actions, controller, identifier };
}

it("loads a create catalog without acquiring an existing definition", async () => {
  const { controller, actions } = fixture(true);
  controller.activate();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  expect(actions.loadRoleTemplate.execute).not.toHaveBeenCalled();
  expect(actions.loadAssignablePermissions.execute).toHaveBeenCalledTimes(1);
  await controller.save(fields);
  expect(actions.saveRoleTemplate.execute.mock.lastCall?.[2]).toMatchObject({
    id: roleTemplateId(),
    expectedVersion: null,
    code: fields.code,
  });
  expect(controller.getSnapshot().stage).toBe("saved");
  controller.deactivate();
});
it.each(["catalog", "review"])(
  "owns and cancels both editor acquisitions when %s throws",
  async (mode) => {
    const { controller, actions } = fixture();
    const deferred = pending<Result<RoleTemplateReview>>();
    const catalog = pending<Result<readonly string[]>>();
    actions.loadRoleTemplate.execute.mockReturnValueOnce(deferred.promise);
    actions.loadAssignablePermissions.execute.mockReturnValueOnce(catalog.promise);
    controller.activate();
    if (mode === "catalog") catalog.reject(new Error("Private failure"));
    else deferred.reject(new Error("Private failure"));
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(actions.loadAssignablePermissions.execute.mock.calls[0]?.[1].aborted).toBe(true);
    expect(actions.loadRoleTemplate.execute.mock.calls[0]?.[3].aborted).toBe(true);
    catalog.resolve(success(roleCatalog));
    deferred.resolve(success(review));
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("editing");
    controller.deactivate();
  },
);
it.each(["catalog", "review"])(
  "does not expose a partial editor after %s is denied",
  async (mode) => {
    const { controller, actions } = fixture();
    if (mode === "catalog")
      actions.loadAssignablePermissions.execute.mockResolvedValueOnce(failed("access_denied"));
    else actions.loadRoleTemplate.execute.mockResolvedValueOnce(failed("role_template_not_found"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    expect(controller.getSnapshot().template).toBeNull();
    expect(controller.getSnapshot().permissions).toEqual([]);
    await controller.save(fields);
    expect(actions.saveRoleTemplate.execute).not.toHaveBeenCalled();
    controller.deactivate();
  },
);
it("snapshots one reviewed command, blocks duplicates and preserves unknown outcomes across MFA and a later conflict", async () => {
  const { controller, actions, identifier } = fixture();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  const delayed = pending<Result<MutationReceipt>>();
  actions.saveRoleTemplate.execute
    .mockReturnValueOnce(delayed.promise)
    .mockResolvedValueOnce(failed("recent_authentication_required"))
    .mockResolvedValueOnce(failed("stale_version"));
  const input = { ...fields, permissions: [...fields.permissions] };
  const saving = controller.save(input);
  input.permissions.push("payroll.read");
  input.name = "Caller mutation";
  await controller.save(fields);
  await controller.refresh();
  await controller.retrySave();
  expect(actions.saveRoleTemplate.execute).toHaveBeenCalledTimes(1);
  expect(identifier).toHaveBeenCalledTimes(1);
  const original = actions.saveRoleTemplate.execute.mock.calls[0];
  expect(original?.[2]).toMatchObject({
    expectedVersion: 1,
    code: review.current.code,
    name: fields.name,
    permissions: fields.permissions,
  });
  expect(Object.isFrozen(original?.[2].permissions)).toBe(true);
  delayed.resolve(failed("network_unavailable"));
  await saving;
  expect(controller.getSnapshot().stage).toBe("unconfirmed");
  await controller.retrySave();
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    failure: { code: "recent_authentication_required" },
  });
  await controller.retrySave();
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    failure: { code: "stale_version" },
  });
  await controller.retrySave();
  expect(controller.getSnapshot().stage).toBe("saved");
  for (const call of actions.saveRoleTemplate.execute.mock.calls) {
    expect(call[1]).toBe(original?.[1]);
    expect(call[2]).toBe(original?.[2]);
  }
  expect(actions.loadRoleTemplate.execute).toHaveBeenCalledTimes(1);
  controller.deactivate();
});
it.each(["stale_version", "role_code_immutable", "role_template_not_found"])(
  "requires a fresh review after a confirmed %s rejection",
  async (code) => {
    const { controller, actions } = fixture();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    actions.saveRoleTemplate.execute.mockResolvedValueOnce(failed(code));
    await controller.save(fields);
    expect(controller.getSnapshot().stage).toBe("conflict");
    expect(controller.getSnapshot().operationId).toBeNull();
    await controller.save(fields);
    await controller.retrySave();
    expect(actions.saveRoleTemplate.execute).toHaveBeenCalledTimes(1);
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("editing");
    expect(actions.loadRoleTemplate.execute).toHaveBeenCalledTimes(2);
    controller.deactivate();
  },
);
it("returns validation feedback to the retained form and treats thrown writes as unconfirmed", async () => {
  const { controller, actions } = fixture();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  actions.saveRoleTemplate.execute
    .mockResolvedValueOnce(failed("invalid_role_template"))
    .mockRejectedValueOnce(new Error("Private transport message"));
  await controller.save(fields);
  expect(controller.getSnapshot().stage).toBe("editing");
  await controller.save(fields);
  expect(controller.getSnapshot().stage).toBe("unconfirmed");
  expect(JSON.stringify(controller.getSnapshot())).not.toContain("Private");
  controller.deactivate();
});
it.each(["resolve", "reject"])(
  "abandons an old mutation and its late %s on disposal",
  async (outcome) => {
    const { controller, actions } = fixture();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const write = pending<Result<MutationReceipt>>();
    actions.saveRoleTemplate.execute.mockReturnValueOnce(write.promise);
    const waiting = controller.save(fields);
    const oldSignal = actions.saveRoleTemplate.execute.mock.calls[0]?.[3];
    controller.deactivate();
    expect(oldSignal?.aborted).toBe(true);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    if (outcome === "resolve") write.resolve(success({ id: roleTemplateId(), version: 2 }));
    else write.reject(new Error("Late private error"));
    await waiting;
    expect(controller.getSnapshot().stage).toBe("editing");
    expect(controller.getSnapshot().operationId).toBeNull();
    controller.deactivate();
  },
);
it("abandons pending editor reads without retaining old account or company data", async () => {
  const { controller, actions } = fixture();
  const read = pending<Result<RoleTemplateReview>>();
  actions.loadRoleTemplate.execute.mockReturnValueOnce(read.promise);
  controller.activate();
  controller.deactivate();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
  expect(actions.loadRoleTemplate.execute.mock.calls[0]?.[3].aborted).toBe(true);
  read.resolve(failed("company_access_denied"));
  await Promise.resolve();
  expect(controller.getSnapshot().template).toBe(review.current);
  controller.deactivate();
});
it("clears selected revision details and cancels stale review results", async () => {
  const load = {
    execute: vi
      .fn<IdentityAdministrationUseCases["loadRoleTemplate"]["execute"]>()
      .mockResolvedValue(success(review)),
  };
  const c = new RoleTemplateController(load, access, roleTemplateId(), null);
  c.activate();
  await vi.waitFor(() => expect(c.getSnapshot().stage).toBe("ready"));
  c.selectRevision("0");
  expect(c.getSnapshot().selectedRevision?.revision).toBe(0);
  const old = pending<Result<RoleTemplateReview>>();
  load.execute
    .mockReturnValueOnce(old.promise)
    .mockResolvedValueOnce(failed("role_template_not_found"));
  const waiting = c.refresh();
  expect(c.getSnapshot().selectedRevision).toBeNull();
  await c.refresh();
  old.resolve(success(review));
  await waiting;
  expect(c.getSnapshot().stage).toBe("unavailable");
  expect(c.getSnapshot().review).toBeNull();
  c.deactivate();
});
it("isolates role directory lifetimes and rejects a late uncooperative failure", async () => {
  const old = pending<Result<RoleTemplatePage>>();
  const load = {
    execute: vi
      .fn<IdentityAdministrationUseCases["loadRoleTemplates"]["execute"]>()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValue(success({ items: [review.current], nextCursor: null })),
  };
  const c = new RoleTemplatesController(load, access, null);
  c.activate();
  c.activate();
  const previous = load.execute.mock.calls[0]?.[2];
  await c.refresh();
  old.reject(new Error("Private late error"));
  await Promise.resolve();
  expect(previous?.aborted).toBe(true);
  expect(c.getSnapshot().stage).toBe("ready");
  c.deactivate();
  expect(c.getSnapshot().page).toBeNull();
});
