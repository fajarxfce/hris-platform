import { expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { AccountCredentialUseCases } from "../contracts/account-credential-use-cases";
import { credentialTokenFromFragment } from "../models/credential-link-route";
import { CredentialConfirmationController } from "./credential-confirmation-controller";
import { PasswordRecoveryController } from "./password-recovery-controller";

const token = "q".repeat(43);
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
    confirmAccountCredential: {
      execute: vi
        .fn<AccountCredentialUseCases["confirmAccountCredential"]["execute"]>()
        .mockResolvedValue(success(undefined)),
    },
    requestPasswordRecovery: {
      execute: vi
        .fn<AccountCredentialUseCases["requestPasswordRecovery"]["execute"]>()
        .mockResolvedValue(success(undefined)),
    },
    signOut: {
      execute: vi
        .fn<AccountCredentialUseCases["signOut"]["execute"]>()
        .mockResolvedValue(success(undefined)),
    },
  };
  return {
    actions,
    confirmation: new CredentialConfirmationController(actions, "invitation"),
    recovery: new PasswordRecoveryController(actions.requestPasswordRecovery),
  };
}
it.each([
  "",
  `?token=${token}`,
  `#token=${token}&token=${token}`,
  `#token=${token}&redirect=https://else.invalid`,
  "#token=broken",
  `#token=%71${token.slice(1)}`,
])("rejects malformed credential fragments", (fragment) => {
  expect(credentialTokenFromFragment(fragment)).toBeNull();
});
it("captures only the expected link and survives mount reactivation without issuing I/O", async () => {
  const f = fixture();
  f.confirmation.capture(credentialTokenFromFragment(`#token=${token}`));
  f.confirmation.activate();
  f.confirmation.deactivate();
  f.confirmation.activate();
  expect(f.confirmation.getSnapshot().stage).toBe("editing");
  expect(f.actions.confirmAccountCredential.execute).not.toHaveBeenCalled();
  await f.confirmation.confirm("Owned password");
  expect(f.actions.confirmAccountCredential.execute).toHaveBeenCalledExactlyOnceWith(
    "invitation",
    { token, password: "Owned password" },
    expect.any(AbortSignal),
  );
  expect(f.confirmation.getSnapshot()).toEqual({ stage: "saved", leaving: false, failure: null });
  expect(JSON.stringify(f.confirmation.getSnapshot())).not.toContain(token);
  await f.confirmation.confirm("Another password");
  expect(f.actions.confirmAccountCredential.execute).toHaveBeenCalledOnce();
  f.confirmation.deactivate();
});
it.each(["connection_unavailable", "request_timeout", "invalid_response"])(
  "does not replay an uncertain one-use confirmation after %s",
  async (code) => {
    const f = fixture();
    f.confirmation.capture(token);
    f.confirmation.activate();
    f.actions.confirmAccountCredential.execute.mockResolvedValueOnce(failed(code));
    await f.confirmation.confirm("Owned password");
    expect(f.confirmation.getSnapshot().stage).toBe("unconfirmed");
    await f.confirmation.confirm("Owned password");
    expect(f.actions.confirmAccountCredential.execute).toHaveBeenCalledOnce();
    await f.confirmation.continueToSignIn();
    expect(f.actions.signOut.execute).toHaveBeenCalledOnce();
    expect(f.confirmation.getSnapshot().stage).toBe("signedOut");
    f.confirmation.deactivate();
  },
);
it("keeps a definitely rejected password editable but retires a consumed link", async () => {
  const f = fixture();
  f.confirmation.capture(token);
  f.confirmation.activate();
  f.actions.confirmAccountCredential.execute
    .mockResolvedValueOnce(failed("password_length"))
    .mockResolvedValueOnce(failed("credential_link_unavailable"));
  await f.confirmation.confirm("short");
  expect(f.confirmation.getSnapshot().stage).toBe("editing");
  await f.confirmation.confirm("Owned password");
  expect(f.confirmation.getSnapshot().stage).toBe("unavailable");
  await f.confirmation.confirm("Owned password");
  expect(f.actions.confirmAccountCredential.execute).toHaveBeenCalledTimes(2);
  f.confirmation.deactivate();
});
it("cancels superseded confirmation and rejects a late completion", async () => {
  const f = fixture();
  const held = pending<Result<void>>();
  f.actions.confirmAccountCredential.execute.mockReturnValueOnce(held.promise);
  f.confirmation.capture(token);
  f.confirmation.activate();
  const submitted = f.confirmation.confirm("Owned password");
  await f.confirmation.confirm("Duplicate password");
  f.confirmation.capture("z".repeat(43));
  expect(f.actions.confirmAccountCredential.execute.mock.calls[0]?.[2].aborted).toBe(true);
  held.resolve(success(undefined));
  await submitted;
  expect(f.confirmation.getSnapshot().stage).toBe("editing");
  await f.confirmation.confirm("Replacement password");
  expect(f.actions.confirmAccountCredential.execute.mock.calls[1]?.[1].token).toBe("z".repeat(43));
  f.confirmation.deactivate();
});
it("aborts pending confirmation and does not publish after its listener is released", async () => {
  const f = fixture();
  const held = pending<Result<void>>();
  f.actions.confirmAccountCredential.execute.mockReturnValueOnce(held.promise);
  f.confirmation.capture(token);
  f.confirmation.activate();
  const listener = vi.fn();
  const unsubscribe = f.confirmation.subscribe(listener);
  const submitted = f.confirmation.confirm("Owned password");
  unsubscribe();
  listener.mockClear();
  f.confirmation.deactivate();
  expect(f.actions.confirmAccountCredential.execute.mock.calls[0]?.[2].aborted).toBe(true);
  held.reject(new Error("Owned late failure"));
  await submitted;
  expect(listener).not.toHaveBeenCalled();
  expect(f.confirmation.getSnapshot().stage).toBe("unconfirmed");
});
it("allows an explicit sign-out retry without repeating credential confirmation", async () => {
  const f = fixture();
  f.confirmation.capture(token);
  f.confirmation.activate();
  await f.confirmation.confirm("Owned password");
  f.actions.signOut.execute.mockResolvedValueOnce(failed("connection_unavailable"));
  await f.confirmation.continueToSignIn();
  expect(f.confirmation.getSnapshot()).toMatchObject({
    stage: "saved",
    leaving: false,
    failure: { code: "connection_unavailable" },
  });
  await f.confirmation.continueToSignIn();
  expect(f.confirmation.getSnapshot().stage).toBe("signedOut");
  expect(f.actions.confirmAccountCredential.execute).toHaveBeenCalledOnce();
  f.confirmation.deactivate();
});
it("recovery owns one request and ignores late failure after departure", async () => {
  const f = fixture();
  const held = pending<Result<void>>();
  f.actions.requestPasswordRecovery.execute.mockReturnValueOnce(held.promise);
  f.recovery.activate();
  const submitted = f.recovery.request("owned@example.test");
  await f.recovery.request("duplicate@example.test");
  expect(f.actions.requestPasswordRecovery.execute).toHaveBeenCalledOnce();
  f.recovery.deactivate();
  expect(f.actions.requestPasswordRecovery.execute.mock.calls[0]?.[1].aborted).toBe(true);
  held.reject(new Error("Owned late failure"));
  await submitted;
  expect(f.recovery.getSnapshot()).toEqual({ stage: "editing", failure: null });
  f.recovery.activate();
  await f.recovery.request("owned@example.test");
  expect(f.recovery.getSnapshot().stage).toBe("sent");
  await f.recovery.request("again@example.test");
  expect(f.actions.requestPasswordRecovery.execute).toHaveBeenCalledTimes(2);
  f.recovery.deactivate();
});
