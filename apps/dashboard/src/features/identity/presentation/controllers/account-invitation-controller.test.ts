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
import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { AccountInvitationController } from "./account-invitation-controller";

const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const dto = managedAccountReview();
const review = toManagedAccountReview(
  { ...dto, account: { ...dto.account, active: false, invitationPending: true } },
  managedAccountId() as AccountId,
);
const fields = () => ({
  email: "owned@example.test",
  displayName: "Owned invitee",
  reason: "Account provisioning",
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
function fixture(id: string | null = null) {
  const actions = {
    loadManagedAccount: {
      execute: vi
        .fn<IdentityAdministrationUseCases["loadManagedAccount"]["execute"]>()
        .mockResolvedValue(success(review)),
    },
    inviteAccount: {
      execute: vi
        .fn<IdentityAdministrationUseCases["inviteAccount"]["execute"]>()
        .mockImplementation(async (_access, _operation, input) =>
          success({ id: input.accountId, version: (input.expectedVersion ?? -1) + 1 }),
        ),
    },
  };
  let next = 0;
  const identifier = vi.fn(
    () => `d5000000-0000-4000-8000-${(++next).toString().padStart(12, "0")}`,
  );
  return {
    actions,
    identifier,
    controller: new AccountInvitationController(actions, access, id, identifier),
  };
}
it("new invitations need no private lookup and retain one command through response loss, MFA and later conflicts", async () => {
  const f = fixture();
  f.controller.activate();
  expect(f.controller.getSnapshot().stage).toBe("editing");
  expect(f.actions.loadManagedAccount.execute).not.toHaveBeenCalled();
  f.actions.inviteAccount.execute
    .mockResolvedValueOnce(failed("connection_unavailable"))
    .mockResolvedValueOnce(failed("mfa_required"))
    .mockResolvedValueOnce(failed("stale_version"));
  const input = fields();
  await f.controller.save(input);
  input.email = "replacement@example.test";
  expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
  await f.controller.refresh();
  await f.controller.save(fields());
  expect(f.actions.inviteAccount.execute).toHaveBeenCalledTimes(1);
  await f.controller.retrySave();
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("saved");
  expect(f.identifier).toHaveBeenCalledTimes(2);
  const original = f.actions.inviteAccount.execute.mock.calls[0]?.slice(0, 3);
  for (const call of f.actions.inviteAccount.execute.mock.calls)
    expect(call.slice(0, 3)).toEqual(original);
  expect(f.actions.inviteAccount.execute.mock.calls[0]?.[2]).toMatchObject({
    email: "owned@example.test",
    expectedVersion: null,
  });
  expect(Object.isFrozen(f.actions.inviteAccount.execute.mock.calls[0]?.[2])).toBe(true);
  f.controller.deactivate();
});
it("resends use the reviewed account email and version", async () => {
  const f = fixture(managedAccountId());
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  await f.controller.save(fields());
  expect(f.actions.inviteAccount.execute.mock.calls[0]?.[2]).toEqual({
    ...fields(),
    accountId: review.account.id,
    email: review.account.email,
    expectedVersion: review.account.version,
  });
  expect(f.identifier).toHaveBeenCalledOnce();
  f.controller.deactivate();
});
it("definite rejection permits an explicit correction while stale resends require a new review", async () => {
  const f = fixture(managedAccountId());
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  f.actions.inviteAccount.execute
    .mockResolvedValueOnce(failed("mail_not_configured"))
    .mockResolvedValueOnce(failed("stale_version"));
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("editing");
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("conflict");
  await f.controller.save(fields());
  expect(f.actions.inviteAccount.execute).toHaveBeenCalledTimes(2);
  f.actions.loadManagedAccount.execute.mockResolvedValueOnce(
    success({ ...review, account: { ...review.account, version: 6 } }),
  );
  await f.controller.refresh();
  await f.controller.save(fields());
  expect(f.actions.inviteAccount.execute.mock.calls[2]?.[2].expectedVersion).toBe(6);
  f.controller.deactivate();
});
it.each([true, false])(
  "does not invite an account without pending eligibility (active=%s)",
  async (active) => {
    const f = fixture(managedAccountId());
    f.actions.loadManagedAccount.execute.mockResolvedValueOnce(
      success({ ...review, account: { ...review.account, active, invitationPending: false } }),
    );
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
    await f.controller.save(fields());
    expect(f.actions.inviteAccount.execute).not.toHaveBeenCalled();
    f.controller.deactivate();
  },
);
it("denies an unprivileged new invitation without acquiring data", () => {
  const f = fixture();
  const controller = new AccountInvitationController(
    f.actions,
    { ...access, permissions: [] },
    null,
    f.identifier,
  );
  controller.activate();
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    failure: { code: "platform_administrator_required" },
  });
  expect(f.actions.loadManagedAccount.execute).not.toHaveBeenCalled();
  controller.deactivate();
});
it("drops a late review and releases subscriptions and a pending command on departure", async () => {
  const f = fixture(managedAccountId());
  const held = pending<Result<ManagedAccountReview>>();
  f.actions.loadManagedAccount.execute.mockReturnValueOnce(held.promise);
  f.controller.activate();
  await f.controller.refresh();
  expect(f.actions.loadManagedAccount.execute.mock.calls[0]?.[2].aborted).toBe(true);
  held.resolve(failed("platform_administrator_required"));
  await Promise.resolve();
  expect(f.controller.getSnapshot().stage).toBe("editing");
  const command = pending<Result<MutationReceipt>>();
  f.actions.inviteAccount.execute.mockReturnValueOnce(command.promise);
  const submitted = f.controller.save(fields());
  await f.controller.save(fields());
  const listener = vi.fn();
  const unsubscribe = f.controller.subscribe(listener);
  unsubscribe();
  f.controller.deactivate();
  expect(f.actions.inviteAccount.execute.mock.calls[0]?.[3].aborted).toBe(true);
  command.reject(new Error("Owned late transport failure"));
  await submitted;
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "loading",
    review: null,
    operationId: null,
  });
  expect(listener).not.toHaveBeenCalled();
  expect(f.actions.inviteAccount.execute).toHaveBeenCalledOnce();
});
