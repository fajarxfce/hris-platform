import { expect, it, vi } from "vitest";
import {
  managedAccountId,
  managedAccountReview,
  platformActorId,
  platformPermissions,
} from "../../../../../tests/fixtures/managed-accounts";
import {
  oidcBinding,
  oidcBindingId,
  oidcIssuer,
} from "../../../../../tests/fixtures/oidc-bindings";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toManagedAccountReview } from "../../data/mappers/managed-account-mapper";
import { toOidcBinding } from "../../data/mappers/oidc-binding-mapper";
import type { OidcBindingDirectory } from "../../domain/entities/oidc-binding-directory";
import type { OidcBindingReview } from "../../domain/entities/oidc-binding-review";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { OidcBindingEditorController } from "./oidc-binding-editor-controller";
import { OidcBindingsController } from "./oidc-bindings-controller";

const accountId = managedAccountId() as AccountId;
const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const account = toManagedAccountReview(managedAccountReview(), accountId).account;
const binding = toOidcBinding(oidcBinding(), accountId);
const review = { account, binding };
const directory = { account, page: { items: [binding], nextCursor: null } };
const fields = () => ({
  issuer: oidcIssuer,
  subject: "Exact / Subject",
  active: false,
  reason: "Reviewed identity",
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
function fixture(creating = false) {
  const actions = {
    prepareOidcBinding: {
      execute: vi
        .fn<IdentityAdministrationUseCases["prepareOidcBinding"]["execute"]>()
        .mockResolvedValue(success({ account, binding: creating ? null : binding })),
    },
    saveOidcBinding: {
      execute: vi
        .fn<IdentityAdministrationUseCases["saveOidcBinding"]["execute"]>()
        .mockImplementation(async (_access, _operation, input) =>
          success({ id: input.id, version: (input.expectedVersion ?? -1) + 1 }),
        ),
    },
  };
  let next = 0;
  const identifier = vi.fn(
    () => `d7000000-0000-4000-8000-${(++next).toString().padStart(12, "0")}`,
  );
  return {
    actions,
    identifier,
    controller: new OidcBindingEditorController(
      actions,
      access,
      accountId,
      creating ? null : oidcBindingId(),
      identifier,
    ),
  };
}

it("pins immutable binding identity and original command through response loss, MFA and later conflicts", async () => {
  const f = fixture();
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  f.actions.saveOidcBinding.execute
    .mockResolvedValueOnce(failed("connection_unavailable"))
    .mockResolvedValueOnce(failed("mfa_required"))
    .mockResolvedValueOnce(failed("stale_version"));
  const edited = fields();
  await f.controller.save(edited);
  edited.reason = "another reason";
  expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
  await f.controller.refresh();
  await f.controller.save(fields());
  expect(f.actions.prepareOidcBinding.execute).toHaveBeenCalledOnce();
  expect(f.actions.saveOidcBinding.execute).toHaveBeenCalledOnce();
  await f.controller.retrySave();
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("saved");
  const original = f.actions.saveOidcBinding.execute.mock.calls[0];
  expect(original?.[2]).toEqual({
    accountId,
    id: binding.id,
    issuer: binding.issuer,
    subject: binding.subject,
    expectedVersion: 3,
    active: false,
    reason: "Reviewed identity",
  });
  for (const call of f.actions.saveOidcBinding.execute.mock.calls)
    expect(call.slice(0, 3)).toEqual(original?.slice(0, 3));
  expect(Object.isFrozen(original?.[2])).toBe(true);
  expect(f.identifier).toHaveBeenCalledOnce();
  f.controller.deactivate();
});
it("creates one identifier and preserves the typed provider subject verbatim", async () => {
  const f = fixture(true);
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  await f.controller.save({ ...fields(), subject: " Case / Exact ", active: true });
  expect(f.actions.saveOidcBinding.execute.mock.calls[0]?.[2]).toMatchObject({
    accountId,
    expectedVersion: null,
    issuer: oidcIssuer,
    subject: " Case / Exact ",
    active: true,
  });
  expect(f.identifier).toHaveBeenCalledTimes(2);
  f.controller.deactivate();
});
it("a definite rejection permits correction while a conflict requires a new review", async () => {
  const f = fixture();
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("editing"));
  await f.controller.save({ ...fields(), active: true });
  expect(f.actions.saveOidcBinding.execute).not.toHaveBeenCalled();
  f.actions.saveOidcBinding.execute
    .mockResolvedValueOnce(failed("invalid_oidc_identity"))
    .mockResolvedValueOnce(failed("stale_version"));
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("editing");
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("conflict");
  await f.controller.save(fields());
  expect(f.actions.saveOidcBinding.execute).toHaveBeenCalledTimes(2);
  f.actions.prepareOidcBinding.execute.mockResolvedValueOnce(
    success({ ...review, binding: { ...binding, version: 5 } }),
  );
  await f.controller.refresh();
  await f.controller.save(fields());
  expect(f.actions.saveOidcBinding.execute.mock.calls[2]?.[2].expectedVersion).toBe(5);
  f.controller.deactivate();
});
it("rejects late review results and releases a pending mutation without duplicate submission", async () => {
  const f = fixture();
  const held = pending<Result<OidcBindingReview>>();
  f.actions.prepareOidcBinding.execute.mockReturnValueOnce(held.promise);
  f.controller.activate();
  await f.controller.refresh();
  expect(f.actions.prepareOidcBinding.execute.mock.calls[0]?.[3].aborted).toBe(true);
  held.resolve(failed("platform_administrator_required"));
  await Promise.resolve();
  expect(f.controller.getSnapshot().stage).toBe("editing");
  const command = pending<Result<MutationReceipt>>();
  f.actions.saveOidcBinding.execute.mockReturnValueOnce(command.promise);
  const submitted = f.controller.save(fields());
  await f.controller.save(fields());
  const listener = vi.fn();
  const unsubscribe = f.controller.subscribe(listener);
  unsubscribe();
  f.controller.deactivate();
  expect(f.actions.saveOidcBinding.execute.mock.calls[0]?.[3].aborted).toBe(true);
  command.reject(new Error("Owned late SSO failure"));
  await submitted;
  expect(f.controller.getSnapshot()).toMatchObject({
    stage: "loading",
    review: null,
    operationId: null,
  });
  expect(listener).not.toHaveBeenCalled();
  expect(f.actions.saveOidcBinding.execute).toHaveBeenCalledOnce();
});
it("treats an unexpected command failure as unconfirmed and prevents writes without a review", async () => {
  const f = fixture();
  await f.controller.save(fields());
  expect(f.actions.saveOidcBinding.execute).not.toHaveBeenCalled();
  f.actions.prepareOidcBinding.execute.mockResolvedValueOnce(
    failed("platform_administrator_required"),
  );
  f.controller.activate();
  await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("unavailable"));
  await f.controller.save(fields());
  expect(f.actions.saveOidcBinding.execute).not.toHaveBeenCalled();
  await f.controller.refresh();
  f.actions.saveOidcBinding.execute.mockRejectedValueOnce(new Error("Owned protocol failure"));
  await f.controller.save(fields());
  expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
  await f.controller.retrySave();
  expect(f.controller.getSnapshot().stage).toBe("saved");
  f.controller.deactivate();
});
it("owns paged reads, clears private data during refresh and ignores disposed results", async () => {
  const execute = vi
    .fn<IdentityAdministrationUseCases["loadOidcBindings"]["execute"]>()
    .mockResolvedValue(success(directory));
  const controller = new OidcBindingsController({ execute }, access, accountId, null);
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  const old = pending<Result<OidcBindingDirectory>>();
  execute.mockReturnValueOnce(old.promise);
  const previous = controller.refresh();
  expect(controller.getSnapshot().directory).toBeNull();
  await controller.refresh();
  expect(execute.mock.calls[1]?.[3].aborted).toBe(true);
  old.resolve(failed("account_not_found"));
  await previous;
  expect(controller.getSnapshot().stage).toBe("ready");
  const disposed = pending<Result<OidcBindingDirectory>>();
  execute.mockReturnValueOnce(disposed.promise);
  const reading = controller.refresh();
  const listener = vi.fn();
  const unsubscribe = controller.subscribe(listener);
  unsubscribe();
  controller.deactivate();
  expect(execute.mock.calls[3]?.[3].aborted).toBe(true);
  disposed.reject(new Error("Late read failure"));
  await reading;
  expect(controller.getSnapshot()).toMatchObject({ stage: "loading", directory: null });
  expect(listener).not.toHaveBeenCalled();
});
