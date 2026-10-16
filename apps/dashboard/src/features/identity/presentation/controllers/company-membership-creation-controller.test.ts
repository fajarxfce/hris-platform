import { expect, it, vi } from "vitest";
import { managedAccountId, platformActorId } from "../../../../../tests/fixtures/managed-accounts";
import { roleTemplateId } from "../../../../../tests/fixtures/role-templates";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccountAccess } from "../../domain/entities/company-account-access";
import type { CompanyMembershipCandidate } from "../../domain/entities/company-membership-candidate";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { CompanyMembershipCreationController } from "./company-membership-creation-controller";

const access: CompanyAccountAccess = {
  company: {
    companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
    permissions: ["identity.manage"],
  },
  platform: { accountId: platformActorId as AccountId, permissions: ["identity.manage"] },
};
const candidate: CompanyMembershipCandidate = Object.freeze({
  accountId: managedAccountId() as AccountId,
  displayName: "Example Colleague",
  email: "colleague@example.invalid",
  assignablePermissions: Object.freeze(["company.read"]),
});
const fields = () => ({
  permissions: ["company.read"],
  roleTemplates: [{ id: roleTemplateId(), version: 3, retainApplied: false }],
  reason: "Reviewed initial company access",
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
function fixture() {
  const actions = {
    prepareCompanyMembership: {
      execute: vi
        .fn<IdentityAdministrationUseCases["prepareCompanyMembership"]["execute"]>()
        .mockResolvedValue(success(candidate)),
    },
    createCompanyMembership: {
      execute: vi
        .fn<IdentityAdministrationUseCases["createCompanyMembership"]["execute"]>()
        .mockResolvedValue(success({ id: candidate.accountId, version: 0 })),
    },
  };
  let next = 0;
  const identifier = vi.fn(
    () => `d4000000-0000-4000-8000-${(++next).toString().padStart(12, "0")}`,
  );
  const controller = new CompanyMembershipCreationController(
    actions,
    access,
    candidate.accountId,
    identifier,
  );
  return { actions, identifier, controller };
}
async function ready(f: ReturnType<typeof fixture>) {
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
}

it("pins the prepared target and detached current role versions while blocking duplicate submission", async () => {
  const f = fixture();
  const response = pending<Result<MutationReceipt>>();
  f.actions.createCompanyMembership.execute.mockReturnValueOnce(response.promise);
  await ready(f);
  f.controller.activate();
  const input = { ...fields(), accountId: managedAccountId(2), expectedVersion: 99, active: false };
  const saving = f.controller.save(input);
  input.permissions.length = 0;
  const [role] = input.roleTemplates;
  if (!role) throw new Error("Missing fixture role");
  role.version = 500;
  input.roleTemplates.length = 0;
  await f.controller.save(fields());
  await f.controller.refresh();
  expect(f.actions.prepareCompanyMembership.execute).toHaveBeenCalledTimes(1);
  expect(f.actions.createCompanyMembership.execute).toHaveBeenCalledTimes(1);
  const sent = f.actions.createCompanyMembership.execute.mock.lastCall?.[2];
  expect(sent).toEqual({ ...fields(), accountId: candidate.accountId });
  expect(Object.isFrozen(sent)).toBe(true);
  expect(Object.isFrozen(sent?.roleTemplates[0])).toBe(true);
  response.resolve(success({ id: candidate.accountId, version: 0 }));
  await saving;
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "saved",
    candidate: null,
    receipt: { version: 0 },
  });
  f.controller.deactivate();
});

it("keeps an unconfirmed creation through MFA and a later existing-member conflict", async () => {
  const f = fixture();
  f.actions.createCompanyMembership.execute
    .mockResolvedValueOnce(failed("connection_unavailable"))
    .mockResolvedValueOnce(failed("mfa_required"))
    .mockResolvedValueOnce(failed("stale_version"));
  await ready(f);
  await f.controller.save(fields());
  const first = f.actions.createCompanyMembership.execute.mock.lastCall?.slice(0, 3);
  for (let retry = 0; retry < 2; retry++) {
    await f.controller.refresh();
    await f.controller.retrySave();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    expect(f.actions.createCompanyMembership.execute.mock.lastCall?.slice(0, 3)).toEqual(first);
  }
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("saved");
  expect(f.actions.createCompanyMembership.execute.mock.lastCall?.slice(0, 3)).toEqual(first);
  expect(f.actions.prepareCompanyMembership.execute).toHaveBeenCalledTimes(1);
  expect(f.identifier).toHaveBeenCalledTimes(1);
  f.controller.deactivate();
});

it("a confirmed concurrent assignment requires review and cannot overwrite the existing membership", async () => {
  const f = fixture();
  f.actions.createCompanyMembership.execute.mockResolvedValueOnce(failed("stale_version"));
  await ready(f);
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("conflict");
  await f.controller.save(fields());
  f.actions.prepareCompanyMembership.execute.mockResolvedValueOnce(
    failed("company_member_already_exists"),
  );
  await f.controller.refresh();
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    candidate: null,
    failure: { code: "company_member_already_exists" },
  });
  await f.controller.save(fields());
  expect(f.actions.createCompanyMembership.execute).toHaveBeenCalledTimes(1);
  f.controller.deactivate();
});

it.each(["stale_role_template", "role_template_unavailable", "operation_payload_mismatch"])(
  "a definitive %s rejection permits corrected input with a new operation",
  async (code) => {
    const f = fixture();
    f.actions.createCompanyMembership.execute.mockResolvedValueOnce(failed(code));
    await ready(f);
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("editing");
    const first = f.actions.createCompanyMembership.execute.mock.lastCall?.[1];
    await f.controller.save({
      ...fields(),
      roleTemplates: [{ id: roleTemplateId(), version: 4, retainApplied: false }],
    });
    expect(f.actions.createCompanyMembership.execute.mock.lastCall?.[1]).not.toBe(first);
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  },
);

it.each(["account_unavailable", "company_member_not_found"])(
  "%s requires a fresh preparation before another write",
  async (code) => {
    const f = fixture();
    f.actions.createCompanyMembership.execute.mockResolvedValueOnce(failed(code));
    await ready(f);
    await f.controller.save(fields());
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.save(fields());
    expect(f.actions.createCompanyMembership.execute).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("editing");
    expect(f.controller.getSnapshot().failure).toBeNull();
    f.controller.deactivate();
  },
);

it.each(["success", "failure", "throw"])(
  "a late preparation %s cannot replace a new review after disposal",
  async (outcome) => {
    const f = fixture();
    const first = pending<Result<CompanyMembershipCandidate>>();
    f.actions.prepareCompanyMembership.execute.mockReturnValueOnce(first.promise);
    f.controller.activate();
    const signal = f.actions.prepareCompanyMembership.execute.mock.lastCall?.[3];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    f.actions.prepareCompanyMembership.execute.mockResolvedValueOnce(
      success({ ...candidate, displayName: "New review" }),
    );
    await ready(f);
    if (outcome === "success") first.resolve(success(candidate));
    else if (outcome === "failure") first.resolve(failed("mfa_required"));
    else first.reject(new Error("Private late acquisition error"));
    await Promise.resolve();
    expect(f.controller.getSnapshot().candidate?.displayName).toBe("New review");
    expect(f.controller.getSnapshot().failure).toBeNull();
    f.controller.deactivate();
  },
);

it.each(["success", "failure", "throw"])(
  "a late creation %s clears with its owner and does not enter a replacement editor",
  async (outcome) => {
    const f = fixture();
    const response = pending<Result<MutationReceipt>>();
    f.actions.createCompanyMembership.execute.mockReturnValueOnce(response.promise);
    await ready(f);
    const saving = f.controller.save(fields());
    const signal = f.actions.createCompanyMembership.execute.mock.lastCall?.[3];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    await ready(f);
    if (outcome === "success") response.resolve(success({ id: candidate.accountId, version: 0 }));
    else if (outcome === "failure") response.resolve(failed("stale_version"));
    else response.reject(new Error("Private late write error"));
    await saving;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      operationId: null,
      receipt: null,
      failure: null,
    });
    f.controller.deactivate();
  },
);

it("unexpected write failure remains unconfirmed until explicit receipt recovery", async () => {
  const f = fixture();
  f.actions.createCompanyMembership.execute.mockRejectedValueOnce(
    new Error("Private write failure"),
  );
  await ready(f);
  await f.controller.save(fields());
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    failure: { code: "unexpected_error" },
  });
  const first = f.actions.createCompanyMembership.execute.mock.lastCall?.slice(0, 3);
  await f.controller.retrySave();
  expect(f.actions.createCompanyMembership.execute.mock.lastCall?.slice(0, 3)).toEqual(first);
  expect(f.controller.getSnapshot().stage).toBe("saved");
  f.controller.deactivate();
});

it("subscription and refresh ownership stop on disposal", async () => {
  const f = fixture();
  const listener = vi.fn();
  const unsubscribe = f.controller.subscribe(listener);
  await ready(f);
  expect(listener).toHaveBeenCalled();
  unsubscribe();
  listener.mockClear();
  f.controller.deactivate();
  await f.controller.refresh();
  await f.controller.retrySave();
  await f.controller.save(fields());
  expect(listener).not.toHaveBeenCalled();
  expect(f.actions.prepareCompanyMembership.execute).toHaveBeenCalledTimes(1);
  expect(f.actions.createCompanyMembership.execute).not.toHaveBeenCalled();
});
