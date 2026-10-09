import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess, Session } from "../../domain/entities/session";
import type { IdentityUseCases } from "../contracts/identity-use-cases";
import { IdentityController } from "./identity-controller";

const company = "caf4bdb3-bce9-4680-86c7-6a51ae171e5b" as CompanyId;
const other = "0af356ae-1af0-40d1-b7e9-b9d315206407" as CompanyId;
const operation = "241de1d5-2589-4583-ae4f-4d9b268ab6dd" as OperationId;
const session: Session = {
  account: {
    id: "b6ca22b6-d2ac-4339-bbda-bbb36af4846c" as AccountId,
    email: "hr@example.test",
    displayName: "Example",
    mfaConfigured: false,
  },
  companies: [
    { id: company, name: "Example", code: "EX", timezone: "Asia/Jakarta" },
    { id: other, name: "Other", code: "OTHER", timezone: "Asia/Jakarta" },
  ],
  permissions: [],
  assurance: {
    required: false,
    verified: false,
    setupAvailable: true,
    validUntil: null,
    recentUntil: null,
  },
};
const controllers: IdentityController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});

function pending<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((accept) => {
    resolve = accept;
  });
  return { promise, resolve };
}

function fixture(current: Result<Session> = success(session)) {
  const useCases = {
    loadSession: {
      execute: vi.fn<IdentityUseCases["loadSession"]["execute"]>().mockResolvedValue(current),
    },
    loadCompanyAccess: {
      execute: vi
        .fn<IdentityUseCases["loadCompanyAccess"]["execute"]>()
        .mockImplementation(async (_session, companyId) =>
          success({ companyId, permissions: ["people.read"] }),
        ),
    },
    loadProviders: {
      execute: vi.fn<IdentityUseCases["loadProviders"]["execute"]>().mockResolvedValue(success([])),
    },
    signIn: {
      execute: vi.fn<IdentityUseCases["signIn"]["execute"]>().mockResolvedValue(success(undefined)),
    },
    signOut: {
      execute: vi
        .fn<IdentityUseCases["signOut"]["execute"]>()
        .mockResolvedValue(success(undefined)),
    },
    beginEnrollment: {
      execute: vi.fn<IdentityUseCases["beginEnrollment"]["execute"]>().mockResolvedValue(
        success({
          operationId: operation,
          secret: "fixture-secret",
          otpauthUri: "otpauth://totp/fixture",
          expiresAt: "2026-10-09T00:15:00Z",
        }),
      ),
    },
    confirmEnrollment: {
      execute: vi
        .fn<IdentityUseCases["confirmEnrollment"]["execute"]>()
        .mockResolvedValue(
          success({ verifiedAt: "2026-10-09T00:00:00Z", recoveryCodes: ["fixture-recovery"] }),
        ),
    },
    verifyMfa: {
      execute: vi
        .fn<IdentityUseCases["verifyMfa"]["execute"]>()
        .mockResolvedValue(success({ verifiedAt: "2026-10-09T00:00:00Z", recoveryCodes: [] })),
    },
  } satisfies IdentityUseCases;
  const clear = vi.fn();
  const nextId = vi.fn(() => operation);
  const controller = new IdentityController(useCases, nextId, clear);
  controllers.push(controller);
  controller.activate();
  return { controller, useCases, clear, nextId };
}

describe("identity screen lifecycle", () => {
  it("cancels a previous company request and rejects its late permissions", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const old = pending<Result<CompanyAccess>>();
    const latest = pending<Result<CompanyAccess>>();
    f.useCases.loadCompanyAccess.execute
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(latest.promise);
    const first = f.controller.selectCompany(other);
    const oldSignal = f.useCases.loadCompanyAccess.execute.mock.lastCall?.[2];
    expect(f.controller.getSnapshot().access).toBeNull();
    const second = f.controller.selectCompany(company);
    expect(oldSignal?.aborted).toBe(true);
    latest.resolve(success({ companyId: company, permissions: ["people.self.read"] }));
    await second;
    old.resolve(success({ companyId: other, permissions: ["payroll.read"] }));
    await first;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      companyId: company,
      access: { permissions: ["people.self.read"] },
    });
    expect(f.clear).toHaveBeenCalled();
  });

  it("can remount after cleanup without allowing a cancelled session to restore private data", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const old = pending<Result<Session>>();
    f.useCases.loadSession.execute.mockReturnValueOnce(old.promise);
    const refresh = f.controller.refreshSession();
    const signal = f.useCases.loadSession.execute.mock.lastCall?.[0];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot().session).toBeNull();
    f.useCases.loadSession.execute.mockResolvedValue(failed("authentication_required"));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("signedOut"));
    old.resolve(success(session));
    await refresh;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "signedOut",
      session: null,
      access: null,
      busy: false,
    });
  });

  it("retains recovery codes until acknowledgement without starting a second request", async () => {
    const f = fixture(success({ ...session, assurance: { ...session.assurance, required: true } }));
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("challenge"));
    await f.controller.beginEnrollment();
    await f.controller.confirmEnrollment("123456");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "recoveryCodes",
      enrollment: null,
      recoveryCodes: ["fixture-recovery"],
    });
    await f.controller.refreshSession();
    expect(f.useCases.loadSession.execute).toHaveBeenCalledTimes(1);
    f.useCases.loadSession.execute.mockResolvedValue(failed("connection_unavailable"));
    await f.controller.acknowledgeRecoveryCodes();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      recoveryCodes: [],
      failure: { code: "connection_unavailable" },
    });
  });

  it("reuses an enrollment operation after a lost response and ignores duplicate confirmation", async () => {
    const f = fixture(success({ ...session, assurance: { ...session.assurance, required: true } }));
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("challenge"));
    f.useCases.beginEnrollment.execute.mockResolvedValueOnce(failed("request_timeout"));
    await f.controller.beginEnrollment();
    await f.controller.beginEnrollment();
    expect(f.useCases.beginEnrollment.execute.mock.calls.map(([id]) => id)).toEqual([
      operation,
      operation,
    ]);
    expect(f.nextId).toHaveBeenCalledTimes(1);
    const confirmation =
      pending<Awaited<ReturnType<IdentityUseCases["confirmEnrollment"]["execute"]>>>();
    f.useCases.confirmEnrollment.execute.mockReturnValueOnce(confirmation.promise);
    const first = f.controller.confirmEnrollment("123456");
    await f.controller.confirmEnrollment("123456");
    await f.controller.refreshSession();
    expect(f.useCases.confirmEnrollment.execute).toHaveBeenCalledTimes(1);
    confirmation.resolve(
      success({ verifiedAt: "2026-10-09T00:00:00Z", recoveryCodes: ["retained-once"] }),
    );
    await first;
    expect(f.controller.getSnapshot().recoveryCodes).toEqual(["retained-once"]);
  });

  it("clears account data immediately on logout even if delivery fails", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const response = pending<Result<void>>();
    f.useCases.signOut.execute.mockReturnValueOnce(response.promise);
    const logout = f.controller.signOut();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "signedOut",
      session: null,
      companyId: null,
      access: null,
      recoveryCodes: [],
    });
    response.resolve(failed("request_timeout"));
    await logout;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "signedOut",
      failure: { code: "request_timeout" },
    });
    expect(f.clear).toHaveBeenCalled();
  });

  it.each(["mfa_enrollment_expired", "mfa_enrollment_changed"])(
    "requires an explicit new enrollment after %s",
    async (code) => {
      const f = fixture(
        success({ ...session, assurance: { ...session.assurance, required: true } }),
      );
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("challenge"));
      await f.controller.beginEnrollment();
      f.useCases.confirmEnrollment.execute.mockResolvedValueOnce(failed(code));
      await f.controller.confirmEnrollment("123456");
      expect(f.controller.getSnapshot()).toMatchObject({ enrollment: null, failure: { code } });
      expect(f.useCases.beginEnrollment.execute).toHaveBeenCalledTimes(1);
      await f.controller.beginEnrollment();
      expect(f.nextId).toHaveBeenCalledTimes(2);
    },
  );

  it("keeps a completed sign-in separate from failed bootstrap and retries only the read", async () => {
    const f = fixture(failed("authentication_required"));
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("signedOut"));
    f.useCases.loadSession.execute.mockResolvedValueOnce(failed("request_timeout"));
    await f.controller.signIn({ email: "hr@example.test", password: "fixture-password" });
    expect(f.controller.getSnapshot().stage).toBe("unavailable");
    f.useCases.loadSession.execute.mockResolvedValueOnce(success(session));
    await f.controller.refreshSession();
    expect(f.controller.getSnapshot().stage).toBe("ready");
    expect(f.useCases.signIn.execute).toHaveBeenCalledTimes(1);
  });
});
