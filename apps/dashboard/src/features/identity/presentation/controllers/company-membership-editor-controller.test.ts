import { expect, it, vi } from "vitest";
import {
  companyMembershipGrant,
  companyMembershipId,
} from "../../../../../tests/fixtures/company-memberships";
import { roleCatalog, roleTemplateId } from "../../../../../tests/fixtures/role-templates";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toCompanyMemberGrant } from "../../data/mappers/company-member-mapper";
import type { CompanyMemberGrant } from "../../domain/entities/company-member";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { CompanyMembershipEditorController } from "./company-membership-editor-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["identity.manage"],
};
const grant = toCompanyMemberGrant(companyMembershipGrant(), companyMembershipId as AccountId);
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
const fields = () => ({
  active: true,
  permissions: ["company.read"],
  roleTemplates: [{ id: roleTemplateId(), version: 0, retainApplied: true }],
  reason: "Reviewed company access",
});
function fixture() {
  const actions = {
    loadCompanyMember: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadCompanyMember"]["execute"]>()
        .mockResolvedValue(success(grant)),
    },
    loadAssignablePermissions: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadAssignablePermissions"]["execute"]>()
        .mockResolvedValue(success(roleCatalog)),
    },
    saveCompanyMembership: {
      execute: vi
        .fn<IdentityAdministrationUseCases["saveCompanyMembership"]["execute"]>()
        .mockResolvedValue(success({ id: companyMembershipId, version: 5 })),
    },
  };
  let next = 0;
  const identifier = vi.fn(
    () => `d2000000-0000-4000-8000-${(++next).toString().padStart(12, "0")}`,
  );
  const controller = new CompanyMembershipEditorController(
    actions,
    access,
    companyMembershipId,
    identifier,
  );
  return { controller, actions, identifier };
}

it.each(["permissions", "grant"])(
  "does not expose a partial editor when %s is denied",
  async (part) => {
    const f = fixture();
    if (part === "permissions")
      f.actions.loadAssignablePermissions.execute.mockResolvedValueOnce(failed("access_denied"));
    else f.actions.loadCompanyMember.execute.mockResolvedValueOnce(failed("mfa_required"));
    f.controller.activate();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
    expect(f.controller.getSnapshot().grant).toBeNull();
    expect(f.controller.getSnapshot().permissions).toEqual([]);
    expect(f.actions.loadCompanyMember.execute).toHaveBeenCalledTimes(1);
    f.controller.deactivate();
  },
);

it.each(["permissions", "grant"])("cancels the other acquisition when %s throws", async (part) => {
  const f = fixture();
  const member = pending<Result<CompanyMemberGrant>>();
  const catalog = pending<Result<readonly string[]>>();
  f.actions.loadCompanyMember.execute.mockReturnValueOnce(member.promise);
  f.actions.loadAssignablePermissions.execute.mockReturnValueOnce(catalog.promise);
  f.controller.activate();
  if (part === "permissions") catalog.reject(new Error("Private failure"));
  else member.reject(new Error("Private failure"));
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
  expect(f.actions.loadCompanyMember.execute.mock.calls[0]?.[2].aborted).toBe(true);
  expect(f.actions.loadAssignablePermissions.execute.mock.calls[0]?.[1].aborted).toBe(true);
  catalog.resolve(success(roleCatalog));
  member.resolve(success(grant));
  await f.controller.refresh();
  expect(f.controller.getSnapshot().stage).toBe("editing");
  f.controller.deactivate();
});

it("pins the reviewed member, version and detached role intent while rejecting duplicate saves", async () => {
  const f = fixture();
  const response = pending<Result<MutationReceipt>>();
  f.actions.saveCompanyMembership.execute.mockReturnValueOnce(response.promise);
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  const input = { ...fields(), accountId: "another-account", expectedVersion: 99 };
  const saving = f.controller.save(input);
  input.permissions.length = 0;
  const [role] = input.roleTemplates;
  if (!role) throw new Error("Missing fixture role");
  role.retainApplied = false;
  input.roleTemplates.length = 0;
  await f.controller.save(fields());
  await f.controller.refresh();
  expect(f.actions.saveCompanyMembership.execute).toHaveBeenCalledTimes(1);
  expect(f.actions.loadCompanyMember.execute).toHaveBeenCalledTimes(1);
  const sent = f.actions.saveCompanyMembership.execute.mock.lastCall?.[2];
  expect(sent).toEqual({ ...fields(), accountId: companyMembershipId, expectedVersion: 4 });
  expect(Object.isFrozen(sent?.roleTemplates)).toBe(true);
  expect(Object.isFrozen(sent?.roleTemplates[0])).toBe(true);
  response.resolve(success({ id: companyMembershipId, version: 5 }));
  await saving;
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "saved",
    grant: null,
    receipt: { version: 5 },
  });
  f.controller.deactivate();
});

it("retains the same unconfirmed membership command through MFA and later version rejection", async () => {
  const f = fixture();
  f.actions.saveCompanyMembership.execute
    .mockResolvedValueOnce(failed("connection_unavailable"))
    .mockResolvedValueOnce(failed("mfa_required"))
    .mockResolvedValueOnce(failed("stale_version"));
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  await f.controller.save(fields());
  const initial = f.actions.saveCompanyMembership.execute.mock.lastCall;
  for (let attempt = 0; attempt < 2; attempt++) {
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.refresh();
    await f.controller.retrySave();
    expect(f.actions.saveCompanyMembership.execute.mock.lastCall?.slice(0, 3)).toEqual(
      initial?.slice(0, 3),
    );
  }
  expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("saved");
  expect(f.identifier).toHaveBeenCalledTimes(1);
  expect(f.actions.loadCompanyMember.execute).toHaveBeenCalledTimes(1);
  f.controller.deactivate();
});

it("requires a fresh membership review after a definite membership conflict", async () => {
  const f = fixture();
  f.actions.saveCompanyMembership.execute.mockResolvedValueOnce(failed("stale_version"));
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("conflict");
  await f.controller.save(fields());
  expect(f.actions.saveCompanyMembership.execute).toHaveBeenCalledTimes(1);
  f.actions.loadCompanyMember.execute.mockResolvedValueOnce(
    success({ ...grant, member: { ...grant.member, version: 5 } }),
  );
  f.actions.saveCompanyMembership.execute.mockResolvedValueOnce(
    success({ id: companyMembershipId, version: 6 }),
  );
  await f.controller.refresh();
  await f.controller.save(fields());
  expect(f.actions.saveCompanyMembership.execute.mock.lastCall?.[2].expectedVersion).toBe(5);
  expect(f.identifier).toHaveBeenCalledTimes(2);
  f.controller.deactivate();
});

it.each([
  "stale_role_template",
  "role_template_review_required",
  "account_unavailable",
  "invalid_membership",
])("keeps the reviewed form editable after a definite %s rejection", async (code) => {
  const f = fixture();
  f.actions.saveCompanyMembership.execute.mockResolvedValueOnce(failed(code));
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  await f.controller.save(fields());
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", grant, operationId: null });
  expect(f.actions.loadCompanyMember.execute).toHaveBeenCalledTimes(1);
  f.controller.deactivate();
});

it.each(["success", "failure", "throw"])(
  "ignores late save %s after disposal and cancels the request",
  async (mode) => {
    const f = fixture();
    const response = pending<Result<MutationReceipt>>();
    f.actions.saveCompanyMembership.execute.mockReturnValueOnce(response.promise);
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
    const saving = f.controller.save(fields());
    f.controller.deactivate();
    expect(f.actions.saveCompanyMembership.execute.mock.lastCall?.[3].aborted).toBe(true);
    if (mode === "throw") response.reject(new Error("Private failure"));
    else
      response.resolve(
        mode === "success"
          ? success({ id: companyMembershipId, version: 5 })
          : failed("access_denied"),
      );
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      grant: null,
      failure: null,
      receipt: null,
    });
    await f.controller.retrySave();
    expect(f.actions.saveCompanyMembership.execute).toHaveBeenCalledTimes(1);
  },
);

it("ignores a late membership read after its editor is removed", async () => {
  const f = fixture();
  const response = pending<Result<CompanyMemberGrant>>();
  f.actions.loadCompanyMember.execute.mockReturnValue(response.promise);
  f.controller.activate();
  const loading = f.controller.refresh();
  f.controller.deactivate();
  expect(f.actions.loadCompanyMember.execute.mock.lastCall?.[2].aborted).toBe(true);
  response.resolve(success(grant));
  await loading;
  expect(f.controller.getSnapshot()).toMatchObject({ stage: "loading", grant: null });
});
