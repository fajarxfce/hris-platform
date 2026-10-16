import { expect, it, vi } from "vitest";
import {
  managedAccountId,
  managedAccountReview,
  platformActorId,
  platformPermissions,
} from "../../../../../tests/fixtures/managed-accounts";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toManagedAccountReview } from "../../data/mappers/managed-account-mapper";
import type { ManagedAccountPage } from "../../domain/entities/managed-account-page";
import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { AccountAccessEditorController } from "./account-access-editor-controller";
import { ManagedAccountController } from "./managed-account-controller";
import { ManagedAccountsController } from "./managed-accounts-controller";

const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const review = toManagedAccountReview(managedAccountReview(), managedAccountId() as AccountId);
const page: ManagedAccountPage = { items: [review.account], nextCursor: null };
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
  active: false,
  platformPermissions: ["companies.create"],
  reason: "Reviewed account access",
});
function fixture() {
  const actions = {
    loadManagedAccounts: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadManagedAccounts"]["execute"]>()
        .mockResolvedValue(success(page)),
    },
    loadManagedAccount: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadManagedAccount"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    saveAccountAccess: {
      execute: vi
        .fn<IdentityAdministrationUseCases["saveAccountAccess"]["execute"]>()
        .mockResolvedValue(success({ id: managedAccountId(), version: 5 })),
    },
  };
  let next = 0;
  const identifier = vi.fn(
    () => `d3000000-0000-4000-8000-${(++next).toString().padStart(12, "0")}`,
  );
  return {
    actions,
    identifier,
    directory: new ManagedAccountsController(
      actions.loadManagedAccounts,
      access,
      "colleague",
      null,
    ),
    detail: new ManagedAccountController(actions.loadManagedAccount, access, managedAccountId()),
    editor: new AccountAccessEditorController(actions, access, managedAccountId(), identifier),
  };
}

it("owns directory reads, drops an obsolete response and clears values after denial", async () => {
  const f = fixture();
  f.directory.activate();
  f.directory.activate();
  await vi.waitFor(() => expect(f.directory.getSnapshot().stage).toBe("ready"));
  expect(f.actions.loadManagedAccounts.execute).toHaveBeenCalledTimes(1);
  const held = pending<Result<ManagedAccountPage>>();
  f.actions.loadManagedAccounts.execute
    .mockReturnValueOnce(held.promise)
    .mockResolvedValueOnce(success({ items: [], nextCursor: null }));
  const old = f.directory.refresh();
  await f.directory.refresh();
  expect(f.actions.loadManagedAccounts.execute.mock.calls[1]?.[3].aborted).toBe(true);
  held.resolve(success(page));
  await old;
  expect(f.directory.getSnapshot().page?.items).toEqual([]);
  f.actions.loadManagedAccounts.execute.mockResolvedValueOnce(
    failed("platform_administrator_required"),
  );
  await f.directory.refresh();
  expect(f.directory.getSnapshot()).toMatchObject({ stage: "unavailable", page: null });
  f.directory.deactivate();
});

it.each(["success", "failure", "throw"])(
  "directory disposal rejects a late %s and releases subscriptions",
  async (mode) => {
    const f = fixture();
    f.directory.activate();
    await vi.waitFor(() => expect(f.directory.getSnapshot().stage).toBe("ready"));
    const held = pending<Result<ManagedAccountPage>>();
    f.actions.loadManagedAccounts.execute.mockReturnValueOnce(held.promise);
    const changed = vi.fn();
    const unsubscribe = f.directory.subscribe(changed);
    const reading = f.directory.refresh();
    unsubscribe();
    changed.mockClear();
    f.directory.deactivate();
    expect(f.actions.loadManagedAccounts.execute.mock.lastCall?.[3].aborted).toBe(true);
    if (mode === "throw") held.reject(new Error("Private fixture failure"));
    else held.resolve(mode === "success" ? success(page) : failed("access_denied"));
    await reading;
    expect(f.directory.getSnapshot()).toMatchObject({
      stage: "loading",
      page: null,
      failure: null,
    });
    expect(changed).not.toHaveBeenCalled();
  },
);

it.each(["detail", "editor"] as const)(
  "%s clears private metadata after refresh failure and ignores a later disposed read",
  async (mode) => {
    const f = fixture();
    const controller = f[mode];
    controller.activate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().review).toEqual(review));
    expect(f.actions.loadManagedAccount.execute).toHaveBeenCalledTimes(1);
    f.actions.loadManagedAccount.execute.mockResolvedValueOnce(failed("mfa_required"));
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    const held = pending<Result<ManagedAccountReview>>();
    f.actions.loadManagedAccount.execute.mockReturnValueOnce(held.promise);
    const reading = controller.refresh();
    controller.deactivate();
    expect(f.actions.loadManagedAccount.execute.mock.lastCall?.[2].aborted).toBe(true);
    held.resolve(success(review));
    await reading;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "loading",
      review: null,
      failure: null,
    });
  },
);

it("captures account identity, version and detached permissions before a pending save", async () => {
  const f = fixture();
  const held = pending<Result<MutationReceipt>>();
  f.actions.saveAccountAccess.execute.mockReturnValueOnce(held.promise);
  f.editor.activate();
  await vi.waitFor(() => expect(f.editor.getSnapshot().stage).toBe("editing"));
  const input = { ...fields(), accountId: "different-account", expectedVersion: 99 };
  const saving = f.editor.save(input);
  input.platformPermissions.length = 0;
  await f.editor.save(fields());
  await f.editor.refresh();
  await f.editor.retrySave();
  expect(f.actions.saveAccountAccess.execute).toHaveBeenCalledTimes(1);
  expect(f.actions.loadManagedAccount.execute).toHaveBeenCalledTimes(1);
  const command = f.actions.saveAccountAccess.execute.mock.lastCall?.[2];
  expect(command).toEqual({ ...fields(), accountId: managedAccountId(), expectedVersion: 4 });
  expect(Object.isFrozen(command?.platformPermissions)).toBe(true);
  held.resolve(success({ id: managedAccountId(), version: 5 }));
  await saving;
  expect(f.editor.getSnapshot()).toMatchObject({
    stage: "saved",
    review: null,
    receipt: { version: 5 },
  });
  f.editor.deactivate();
});

it("keeps the same unconfirmed command across MFA and a later conflict without automatic retry", async () => {
  const f = fixture();
  f.actions.saveAccountAccess.execute
    .mockResolvedValueOnce(failed("connection_unavailable"))
    .mockResolvedValueOnce(failed("mfa_required"))
    .mockResolvedValueOnce(failed("stale_version"));
  f.editor.activate();
  await vi.waitFor(() => expect(f.editor.getSnapshot().stage).toBe("editing"));
  await f.editor.save(fields());
  const first = f.actions.saveAccountAccess.execute.mock.lastCall;
  for (let attempt = 0; attempt < 3; attempt++) {
    expect(f.editor.getSnapshot().stage).toBe("unconfirmed");
    await f.editor.refresh();
    await f.editor.save(fields());
    await f.editor.retrySave();
    expect(f.actions.saveAccountAccess.execute.mock.lastCall?.slice(0, 3)).toEqual(
      first?.slice(0, 3),
    );
  }
  expect(f.editor.getSnapshot().stage).toBe("saved");
  expect(f.identifier).toHaveBeenCalledTimes(1);
  expect(f.actions.loadManagedAccount.execute).toHaveBeenCalledTimes(1);
  f.editor.deactivate();
});

it.each(["stale_version", "account_not_found", "invitation_acceptance_required"])(
  "requires a current account review after definite %s",
  async (code) => {
    const f = fixture();
    f.actions.saveAccountAccess.execute.mockResolvedValueOnce(failed(code));
    f.editor.activate();
    await vi.waitFor(() => expect(f.editor.getSnapshot().stage).toBe("editing"));
    await f.editor.save(fields());
    expect(f.editor.getSnapshot().stage).toBe("conflict");
    await f.editor.save(fields());
    expect(f.identifier).toHaveBeenCalledTimes(1);
    f.actions.loadManagedAccount.execute.mockResolvedValueOnce(
      success({ ...review, account: { ...review.account, version: 5 } }),
    );
    f.actions.saveAccountAccess.execute.mockResolvedValueOnce(
      success({ id: managedAccountId(), version: 6 }),
    );
    await f.editor.refresh();
    await f.editor.save(fields());
    expect(f.actions.saveAccountAccess.execute.mock.lastCall?.[2].expectedVersion).toBe(5);
    expect(f.identifier).toHaveBeenCalledTimes(2);
    f.editor.deactivate();
  },
);

it.each([
  "invalid_account_access",
  "platform_administrator_required",
  "operation_payload_mismatch",
])(
  "allows correction after a definite %s without discarding the observed account",
  async (code) => {
    const f = fixture();
    f.actions.saveAccountAccess.execute.mockResolvedValueOnce(failed(code));
    f.editor.activate();
    await vi.waitFor(() => expect(f.editor.getSnapshot().stage).toBe("editing"));
    await f.editor.save(fields());
    expect(f.editor.getSnapshot()).toMatchObject({ stage: "editing", review, operationId: null });
    expect(f.actions.loadManagedAccount.execute).toHaveBeenCalledTimes(1);
    f.editor.deactivate();
  },
);

it.each(["success", "failure", "throw"])(
  "editor disposal cancels a pending save and ignores late %s",
  async (mode) => {
    const f = fixture();
    const held = pending<Result<MutationReceipt>>();
    f.actions.saveAccountAccess.execute.mockReturnValueOnce(held.promise);
    f.editor.activate();
    await vi.waitFor(() => expect(f.editor.getSnapshot().stage).toBe("editing"));
    const saving = f.editor.save(fields());
    f.editor.deactivate();
    expect(f.actions.saveAccountAccess.execute.mock.lastCall?.[3].aborted).toBe(true);
    if (mode === "throw") held.reject(new Error("Private failure"));
    else
      held.resolve(
        mode === "success"
          ? success({ id: managedAccountId(), version: 5 })
          : failed("session_revoked"),
      );
    await saving;
    expect(f.editor.getSnapshot()).toMatchObject({
      stage: "loading",
      review: null,
      receipt: null,
      failure: null,
    });
    await f.editor.retrySave();
    expect(f.actions.saveAccountAccess.execute).toHaveBeenCalledTimes(1);
  },
);

it("an unexpected save failure stays unconfirmed and can only retry the original command", async () => {
  const f = fixture();
  f.actions.saveAccountAccess.execute.mockRejectedValueOnce(new Error("Private save detail"));
  f.editor.activate();
  await vi.waitFor(() => expect(f.editor.getSnapshot().stage).toBe("editing"));
  await f.editor.save(fields());
  expect(f.editor.getSnapshot()).toMatchObject({
    stage: "unconfirmed",
    failure: { code: "unexpected_error" },
  });
  expect(JSON.stringify(f.editor.getSnapshot())).not.toContain("Private save detail");
  await f.editor.retrySave();
  expect(f.editor.getSnapshot().stage).toBe("saved");
  expect(f.identifier).toHaveBeenCalledTimes(1);
  f.editor.deactivate();
});
