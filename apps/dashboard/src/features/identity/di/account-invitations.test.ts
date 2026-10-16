import { expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, OperationId } from "../../../core/domain/identifiers";
import type { AccountInvitation } from "../domain/entities/account-invitation";
import { createIdentityFeature } from "./identity-feature";

const id = "d5000000-0000-4000-8000-000000000001" as AccountId;
const operation = "d5000000-0000-4000-8000-000000000002" as OperationId;
const access = {
  accountId: "d5000000-0000-4000-8000-000000000003" as AccountId,
  permissions: ["identity.manage"],
};
const input = (expectedVersion: number | null = null): AccountInvitation => ({
  accountId: id,
  email: " Owned+HR@example.test ",
  displayName: " Owned colleague ",
  reason: " Provisioning ",
  expectedVersion,
});
const signal = () => new AbortController().signal;

it.each([null, 7])(
  "invites with the exact normalized identity and version %s",
  async (expected) => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id.toUpperCase(), version: (expected ?? -1) + 1 });
    const result = await createIdentityFeature({ request }).inviteAccount.execute(
      access,
      operation,
      input(expected),
      signal(),
    );
    expect(result).toEqual({ ok: true, value: { id, version: (expected ?? -1) + 1 } });
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path: "/api/v1/identity/invitations",
        method: "POST",
        operationId: operation,
        body: {
          id,
          email: "owned+hr@example.test",
          displayName: "Owned colleague",
          reason: "Provisioning",
          expectedVersion: expected,
        },
      },
      expect.any(AbortSignal),
    );
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
  },
);
it.each([
  "x@example.test\r\nBcc:someone@example.test",
  "group:x@example.test;",
  ".x@example.test",
  "x..x@example.test",
  "x@example..test",
  "x@-example.test",
  "x@example.test,y@example.test",
  "x@example.test@else.test",
  "x@例.test",
])("rejects malformed invitation addresses before transport: %s", async (email) => {
  const request = vi.fn<HttpClient["request"]>();
  const result = await createIdentityFeature({ request }).inviteAccount.execute(
    access,
    operation,
    { ...input(), email },
    signal(),
  );
  expect(result).toMatchObject({
    ok: false,
    failure: { code: "invalid_invitation", fields: { email: "invalid_email" } },
  });
  expect(request).not.toHaveBeenCalled();
});
it.each([-1, 1.5, Number.MAX_SAFE_INTEGER, Number.NaN])(
  "rejects invalid observed versions %s",
  async (expectedVersion) => {
    const request = vi.fn<HttpClient["request"]>();
    expect(
      await createIdentityFeature({ request }).inviteAccount.execute(
        access,
        operation,
        input(expectedVersion),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_invitation" } });
    expect(request).not.toHaveBeenCalled();
  },
);
it("requires original global authority, valid operation and a live caller", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const action = createIdentityFeature({ request }).inviteAccount;
  expect(
    await action.execute({ ...access, permissions: [] }, operation, input(), signal()),
  ).toMatchObject({ ok: false, failure: { code: "platform_administrator_required" } });
  expect(await action.execute(access, "../bad" as OperationId, input(), signal())).toMatchObject({
    ok: false,
    failure: { code: "invalid_operation_id" },
  });
  const cancelled = new AbortController();
  cancelled.abort();
  expect(() => action.execute(access, operation, input(), cancelled.signal)).toThrow();
  expect(request).not.toHaveBeenCalled();
});
it.each([
  { id: access.accountId, version: 0 },
  { id, version: 1 },
  { id, version: -1 },
])("rejects a mismatched or malformed receipt", async (receipt) => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(receipt);
  expect(
    await createIdentityFeature({ request }).inviteAccount.execute(
      access,
      operation,
      input(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});
it("preserves safe failure classifications and cancellation after a late result", async () => {
  const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
    new HttpResponseError(503, {
      code: "mail_not_configured",
      detail: "private provider details",
    }),
  );
  const action = createIdentityFeature({ request }).inviteAccount;
  expect(await action.execute(access, operation, input(), signal())).toEqual({
    ok: false,
    failure: { code: "mail_not_configured", fields: {}, parameters: {} },
  });
  const caller = new AbortController();
  request.mockImplementationOnce(async () => {
    caller.abort();
    return { id, version: 0 };
  });
  await expect(action.execute(access, operation, input(), caller.signal)).rejects.toMatchObject({
    name: "AbortError",
  });
});
