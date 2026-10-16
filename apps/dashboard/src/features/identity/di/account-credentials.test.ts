import { expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountCredentialKind } from "../domain/entities/account-credential";
import { createIdentityFeature } from "./identity-feature";

const token = "a".repeat(43);
const signal = () => new AbortController().signal;

it.each(["invitation", "passwordRecovery"] as const)(
  "confirms %s through its exact public endpoint without changing password whitespace",
  async (kind) => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(null);
    const password = "  Owned password  ";
    expect(
      await createIdentityFeature({ request }).confirmAccountCredential.execute(
        kind,
        { token, password },
        signal(),
      ),
    ).toEqual({ ok: true, value: undefined });
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path:
          kind === "invitation"
            ? "/api/v1/auth/invitations/accept"
            : "/api/v1/auth/password-recovery/confirm",
        method: "POST",
        response: { type: "empty", status: 204 },
        body: { token, password },
      },
      expect.any(AbortSignal),
    );
  },
);
it.each(["", "a".repeat(42), "a".repeat(44), `${"a".repeat(42)}?`, `${"a".repeat(42)}é`])(
  "rejects invalid tokens before transport",
  async (invalid) => {
    const request = vi.fn<HttpClient["request"]>();
    expect(
      await createIdentityFeature({ request }).confirmAccountCredential.execute(
        "invitation",
        { token: invalid, password: "Owned password only" },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "credential_link_unavailable" } });
    expect(request).not.toHaveBeenCalled();
  },
);
it.each([11, 129])("rejects a password of length %s before transport", async (length) => {
  const request = vi.fn<HttpClient["request"]>();
  expect(
    await createIdentityFeature({ request }).confirmAccountCredential.execute(
      "invitation",
      { token, password: "a".repeat(length) },
      signal(),
    ),
  ).toMatchObject({
    ok: false,
    failure: { code: "password_length", fields: { password: "length_12_128" } },
  });
  expect(request).not.toHaveBeenCalled();
});
it("rejects an unknown purpose and a cancelled caller", () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  const caller = new AbortController();
  caller.abort();
  expect(() =>
    f.confirmAccountCredential.execute(
      "invitation",
      { token, password: "Owned password" },
      caller.signal,
    ),
  ).toThrow();
  expect(() => f.requestPasswordRecovery.execute("owned@example.test", caller.signal)).toThrow();
  return expect(
    f.confirmAccountCredential.execute(
      "other" as AccountCredentialKind,
      { token, password: "Owned password" },
      signal(),
    ),
  ).resolves.toMatchObject({ ok: false });
});
it("keeps the public recovery acknowledgement independent of account eligibility", async () => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(null);
  const f = createIdentityFeature({ request });
  for (const email of ["missing@example.test", "inactive@example.test", " Owned@example.test "]) {
    expect(await f.requestPasswordRecovery.execute(email, signal())).toEqual({
      ok: true,
      value: undefined,
    });
  }
  expect(request.mock.calls[2]?.[0]).toEqual({
    path: "/api/v1/auth/password-recovery",
    method: "POST",
    response: { type: "empty", status: 202 },
    body: { email: "owned@example.test" },
  });
  expect(await f.requestPasswordRecovery.execute("broken", signal())).toMatchObject({
    ok: false,
    failure: { code: "invalid_recovery_request" },
  });
  expect(request).toHaveBeenCalledTimes(3);
});
it("maps failures without exposing their raw text and rejects nonempty acknowledgements", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockRejectedValueOnce(
      new HttpResponseError(422, {
        code: "credential_link_unavailable",
        detail: "owned raw diagnostic",
      }),
    )
    .mockResolvedValueOnce({ accountId: "not part of this response" });
  const f = createIdentityFeature({ request });
  expect(
    await f.confirmAccountCredential.execute(
      "invitation",
      { token, password: "Owned password" },
      signal(),
    ),
  ).toEqual({
    ok: false,
    failure: { code: "credential_link_unavailable", fields: {}, parameters: {} },
  });
  expect(await f.requestPasswordRecovery.execute("owned@example.test", signal())).toMatchObject({
    ok: false,
    failure: { code: "invalid_response" },
  });
});
