import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { createIdentityFeature } from "./identity-feature";

const companyId = "9365c311-e3b3-45f4-9ae2-2e1d525b1949" as CompanyId;
const otherCompanyId = "a4bd2eab-a92d-487a-b2f4-e14bce0a8f43" as CompanyId;
const operationId = "774cb0d3-8a55-43a2-b8a9-0b9d38516ef0" as OperationId;
const signal = () => new AbortController().signal;
const sessionDto = {
  account: {
    id: "8771789b-5016-40c6-8c86-7b914d34254a",
    email: "hr@example.test",
    displayName: "Example account",
    mfaConfigured: true,
  },
  companies: [
    { id: companyId, code: "EXAMPLE", name: "Example company", timezone: "Asia/Jakarta" },
  ],
  permissions: [],
  assurance: {
    required: true,
    verified: false,
    setupAvailable: true,
    validUntil: null,
    recentUntil: null,
  },
};

function fixture() {
  const request = vi.fn<HttpClient["request"]>();
  return { request, feature: createIdentityFeature({ request }) };
}

describe("identity feature boundaries", () => {
  it("keeps sign-in success separate from a later bootstrap request", async () => {
    const { feature, request } = fixture();
    request.mockResolvedValueOnce({ mfaConfigured: true });
    expect(
      await feature.signIn.execute(
        { email: " HR@EXAMPLE.TEST ", password: "example-password" },
        signal(),
      ),
    ).toEqual({ ok: true, value: undefined });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0]?.[0]).toMatchObject({
      path: "/api/v1/auth/login",
      body: { email: "hr@example.test", password: "example-password" },
    });
    request.mockResolvedValueOnce(sessionDto);
    const session = await feature.loadSession.execute(signal());
    expect(session).toMatchObject({
      ok: true,
      value: { assurance: { required: true, verified: false } },
    });
  });

  it("rejects an unselected company before I/O and rejects a cross-company access response", async () => {
    const { feature, request } = fixture();
    request.mockResolvedValueOnce(sessionDto);
    const loaded = await feature.loadSession.execute(signal());
    if (!loaded.ok) throw new Error("Fixture did not load");
    request.mockClear();
    expect(
      await feature.loadCompanyAccess.execute(loaded.value, otherCompanyId, signal()),
    ).toMatchObject({ ok: false, failure: { code: "company_access_denied" } });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValueOnce({ companyId: otherCompanyId, permissions: ["payroll.read"] });
    expect(
      await feature.loadCompanyAccess.execute(loaded.value, companyId, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("preserves one-use recovery codes before any subsequent session refresh", async () => {
    const { feature, request } = fixture();
    request.mockResolvedValueOnce({
      verifiedAt: "2026-10-09T00:00:00Z",
      recoveryCodes: ["fixture-only-code"],
    });
    const result = await feature.confirmEnrollment.execute(operationId, "123456", signal());
    expect(result).toMatchObject({ ok: true, value: { recoveryCodes: ["fixture-only-code"] } });
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0]?.[0]).toMatchObject({ body: { operationId, code: "123456" } });
    request.mockRejectedValueOnce(new TypeError("Network disconnected"));
    expect(await feature.loadSession.execute(signal())).toMatchObject({
      ok: false,
      failure: { code: "connection_unavailable" },
    });
    expect(result).toMatchObject({ ok: true, value: { recoveryCodes: ["fixture-only-code"] } });
  });

  it("bounds input before authentication and validates provider navigation destinations", async () => {
    const { feature, request } = fixture();
    expect(await feature.signIn.execute({ email: "bad", password: "" }, signal())).toMatchObject({
      ok: false,
    });
    expect(await feature.verifyMfa.execute("not a code", false, signal())).toMatchObject({
      ok: false,
    });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValueOnce([
      {
        id: "company",
        name: "Company SSO",
        authorizationPath: "https://untrusted.example/collect",
      },
    ]);
    expect(await feature.loadProviders.execute(signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it("keeps backend field codes when correlation metadata is absent and discards cancelled bootstrap", async () => {
    const { feature, request } = fixture();
    request.mockRejectedValueOnce(
      new HttpResponseError(401, { code: "session_revoked", correlationId: null }),
    );
    expect(await feature.loadSession.execute(signal())).toMatchObject({
      ok: false,
      failure: { code: "session_revoked" },
    });
    const controller = new AbortController();
    request.mockImplementationOnce(async () => {
      controller.abort();
      return sessionDto;
    });
    await expect(feature.loadSession.execute(controller.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
