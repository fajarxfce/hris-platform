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
  it.each(["begin", "confirm"])(
    "purges retained scope when %s enrollment reports revoked credentials",
    async (step) => {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
      await f.controller.requestVerification();
      expect(f.controller.getSnapshot().workspace).not.toBeNull();
      if (step === "begin") {
        f.useCases.beginEnrollment.execute.mockResolvedValueOnce(failed("session_revoked"));
        await f.controller.beginEnrollment();
      } else {
        await f.controller.beginEnrollment();
        f.useCases.confirmEnrollment.execute.mockResolvedValueOnce(failed("session_revoked"));
        await f.controller.confirmEnrollment("123456");
      }
      expect(f.controller.getSnapshot()).toMatchObject({
        stage: "signedOut",
        workspace: null,
        session: null,
        enrollment: null,
        recoveryCodes: [],
      });
    },
  );

  it("retains the same workspace and request inputs until both foreground checks complete", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const workspace = f.controller.getSnapshot().workspace;
    const access = f.controller.getSnapshot().access;
    const current = pending<Result<Session>>();
    const grants = pending<Result<CompanyAccess>>();
    f.useCases.loadSession.execute.mockReturnValueOnce(current.promise);
    f.useCases.loadCompanyAccess.execute.mockReturnValueOnce(grants.promise);
    f.clear.mockClear();
    const refresh = f.controller.refreshSession();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "loading", access: null, workspace });
    current.resolve(
      success({ ...session, account: { ...session.account, displayName: "Updated display name" } }),
    );
    await vi.waitFor(() => expect(f.useCases.loadCompanyAccess.execute).toHaveBeenCalledTimes(2));
    expect(f.controller.getSnapshot().stage).toBe("loading");
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    grants.resolve(success({ companyId: company, permissions: ["people.read"] }));
    await refresh;
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    expect(f.controller.getSnapshot().access).toBe(access);
    expect(f.controller.getSnapshot().stage).toBe("ready");
    expect(f.clear).not.toHaveBeenCalled();
  });

  it("retains hidden scope across redacted MFA metadata and restores it only after fresh company access", async () => {
    const verified: Session = {
      ...session,
      account: { ...session.account, mfaConfigured: true },
      assurance: { ...session.assurance, required: true, verified: true },
    };
    const f = fixture(success(verified));
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const workspace = f.controller.getSnapshot().workspace;
    f.useCases.loadSession.execute.mockResolvedValueOnce(
      success({
        ...verified,
        companies: [],
        permissions: [],
        assurance: { ...verified.assurance, verified: false },
      }),
    );
    await f.controller.refreshSession();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "challenge",
      verification: "required",
      companyId: company,
      access: null,
    });
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    expect(f.useCases.loadCompanyAccess.execute).toHaveBeenCalledTimes(1);
    const grants = pending<Result<CompanyAccess>>();
    f.useCases.loadCompanyAccess.execute.mockReturnValueOnce(grants.promise);
    const verification = f.controller.verifyMfa("123456", false);
    await vi.waitFor(() => expect(f.useCases.loadCompanyAccess.execute).toHaveBeenCalledTimes(2));
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      verification: "required",
      access: null,
    });
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    grants.resolve(success({ companyId: company, permissions: ["people.read"] }));
    await verification;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", verification: null });
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    expect(f.useCases.verifyMfa.execute).toHaveBeenCalledTimes(1);
  });

  it("keeps a successful MFA proof separate from failed scope recovery and retries only the read", async () => {
    const f = fixture(
      success({ ...session, account: { ...session.account, mfaConfigured: true } }),
    );
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const workspace = f.controller.getSnapshot().workspace;
    await f.controller.requestVerification();
    expect(f.controller.getSnapshot().verification).toBe("requested");
    f.useCases.loadCompanyAccess.execute.mockResolvedValueOnce(failed("connection_unavailable"));
    await f.controller.verifyMfa("123456", false);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      access: null,
      verification: "requested",
    });
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    await f.controller.refreshSession();
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    expect(f.controller.getSnapshot().stage).toBe("ready");
    expect(f.useCases.verifyMfa.execute).toHaveBeenCalledTimes(1);
  });

  it("replaces the workspace on account, company, membership, timezone or permission changes", async () => {
    for (const change of [
      "account",
      "company",
      "membership",
      "timezone",
      "companyPermission",
      "platformPermission",
    ]) {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
      const workspace = f.controller.getSnapshot().workspace;
      const next: Session = {
        ...session,
        account:
          change === "account"
            ? { ...session.account, id: "10000000-0000-4000-8000-000000000009" as AccountId }
            : session.account,
        companies:
          change === "company"
            ? session.companies.filter((item) => item.id === other)
            : change === "membership"
              ? session.companies.filter((item) => item.id === company)
              : change === "timezone"
                ? session.companies.map((item) =>
                    item.id === company ? { ...item, timezone: "UTC" } : item,
                  )
                : session.companies,
        permissions: change === "platformPermission" ? ["companies.create"] : session.permissions,
      };
      f.useCases.loadSession.execute.mockResolvedValueOnce(success(next));
      if (change === "companyPermission")
        f.useCases.loadCompanyAccess.execute.mockResolvedValueOnce(
          success({ companyId: company, permissions: [] }),
        );
      f.clear.mockClear();
      await f.controller.refreshSession();
      expect(f.controller.getSnapshot().workspace).not.toBe(workspace);
      expect(f.controller.getSnapshot().workspace?.revision).toBeGreaterThan(
        workspace?.revision ?? 0,
      );
      expect(f.clear).toHaveBeenCalled();
      f.controller.deactivate();
    }
  });

  it("does not treat reordered equivalent grants or memberships as a new workspace", async () => {
    const f = fixture();
    f.useCases.loadCompanyAccess.execute.mockResolvedValue(
      success({ companyId: company, permissions: ["people.read", "company.read"] }),
    );
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const workspace = f.controller.getSnapshot().workspace;
    f.useCases.loadSession.execute.mockResolvedValueOnce(
      success({ ...session, companies: [...session.companies].reverse() }),
    );
    f.useCases.loadCompanyAccess.execute.mockResolvedValueOnce(
      success({ companyId: company, permissions: ["company.read", "people.read"] }),
    );
    await f.controller.refreshSession();
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
  });

  it("cannot dismiss an optional verification into a session that now requires MFA", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const workspace = f.controller.getSnapshot().workspace;
    await f.controller.requestVerification();
    expect(f.controller.getSnapshot().verification).toBe("requested");
    f.useCases.loadSession.execute.mockResolvedValueOnce(
      success({
        ...session,
        companies: [],
        assurance: { ...session.assurance, required: true, verified: false },
      }),
    );
    await f.controller.cancelVerification();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "challenge",
      verification: "required",
      access: null,
    });
    expect(f.controller.getSnapshot().workspace).toBe(workspace);
    const reads = f.useCases.loadSession.execute.mock.calls.length;
    await f.controller.cancelVerification();
    expect(f.useCases.loadSession.execute).toHaveBeenCalledTimes(reads);
    expect(f.useCases.verifyMfa.execute).not.toHaveBeenCalled();
  });

  it("purges retained form scope when verification or the following access read detects revocation", async () => {
    for (const failureAt of ["proof", "access", "session"]) {
      const f = fixture();
      await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
      await f.controller.requestVerification();
      if (failureAt === "proof")
        f.useCases.verifyMfa.execute.mockResolvedValueOnce(failed("session_revoked"));
      if (failureAt === "session")
        f.useCases.loadSession.execute.mockResolvedValueOnce(failed("session_revoked"));
      if (failureAt === "access")
        f.useCases.loadCompanyAccess.execute.mockResolvedValueOnce(failed("company_access_denied"));
      await f.controller.verifyMfa("123456", false);
      expect(f.controller.getSnapshot().workspace).toBeNull();
      expect(f.controller.getSnapshot().access).toBeNull();
      expect(f.controller.getSnapshot().stage).toBe(
        failureAt === "access" ? "unavailable" : "signedOut",
      );
      f.controller.deactivate();
    }
  });

  it("cancels pending verification on logout and cannot restore its previous workspace", async () => {
    const f = fixture();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    await f.controller.requestVerification();
    const proof = pending<Awaited<ReturnType<IdentityUseCases["verifyMfa"]["execute"]>>>();
    f.useCases.verifyMfa.execute.mockReturnValueOnce(proof.promise);
    const verifying = f.controller.verifyMfa("123456", false);
    const proofSignal = f.useCases.verifyMfa.execute.mock.lastCall?.[2];
    const reads = f.useCases.loadSession.execute.mock.calls.length;
    await f.controller.signOut();
    expect(proofSignal?.aborted).toBe(true);
    proof.resolve(success({ verifiedAt: "2026-10-09T00:00:00Z", recoveryCodes: [] }));
    await verifying;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "signedOut",
      session: null,
      workspace: null,
    });
    expect(f.useCases.loadSession.execute).toHaveBeenCalledTimes(reads);
  });

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
