import { expect, it, vi } from "vitest";
import {
  managedAccountId,
  managedAccountReview,
  platformActorId,
  platformPermissions,
} from "../../../../tests/fixtures/managed-accounts";
import { oidcBinding, oidcBindingId, oidcIssuer } from "../../../../tests/fixtures/oidc-bindings";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, OperationId } from "../../../core/domain/identifiers";
import type { OidcBindingId } from "../domain/entities/oidc-binding";
import type { OidcBindingChange } from "../domain/entities/oidc-binding-change";
import { createIdentityFeature } from "./identity-feature";

const accountId = managedAccountId() as AccountId;
const bindingId = oidcBindingId() as OidcBindingId;
const operation = "d7000000-0000-4000-8000-000000000001" as OperationId;
const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const signal = () => new AbortController().signal;
const input = (expectedVersion: number | null = null): OidcBindingChange => ({
  accountId,
  id: bindingId,
  issuer: oidcIssuer,
  subject: "  Case Sensitive / Subject  ",
  active: true,
  expectedVersion,
  reason: " Reviewed provider account ",
});

it.each([null, 3])(
  "saves the exact issuer and opaque subject with observed version %s",
  async (version) => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: bindingId.toUpperCase(), version: (version ?? -1) + 1 });
    const result = await createIdentityFeature({ request }).saveOidcBinding.execute(
      access,
      operation,
      input(version),
      signal(),
    );
    expect(result).toEqual({ ok: true, value: { id: bindingId, version: (version ?? -1) + 1 } });
    expect(request).toHaveBeenCalledExactlyOnceWith(
      {
        path: `/api/v1/identity/accounts/${accountId}/oidc/${bindingId}`,
        method: "PUT",
        operationId: operation,
        body: {
          issuer: oidcIssuer,
          subject: input().subject,
          active: true,
          expectedVersion: version,
          reason: "Reviewed provider account",
        },
      },
      expect.any(AbortSignal),
    );
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
  },
);
it("maps a bounded page into immutable account-owned records and pins its cursor", async () => {
  const items = Array.from({ length: 50 }, (_, i) => oidcBinding(i + 1));
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(managedAccountReview())
    .mockResolvedValueOnce({ items, nextCursor: oidcBindingId(50) });
  const result = await createIdentityFeature({ request }).loadOidcBindings.execute(
    access,
    accountId,
    null,
    signal(),
  );
  expect(result).toMatchObject({
    ok: true,
    value: {
      account: { id: accountId },
      page: { items: expect.any(Array), nextCursor: oidcBindingId(50) },
    },
  });
  expect(request.mock.calls[1]?.[0].path).toBe(
    `/api/v1/identity/accounts/${accountId}/oidc?size=50`,
  );
  if (result.ok) {
    expect(result.value.page.items[0]).toMatchObject({ id: bindingId, accountId });
    expect(Object.isFrozen(result.value.page.items)).toBe(true);
    expect(Object.isFrozen(result.value.page.items[0])).toBe(true);
    const first = items[0];
    if (first) first.subject = "mutated DTO";
    expect(result.value.page.items[0]?.subject).toBe("Owned-Subject-1");
  }
});
it("reviews one exact binding while a new binding only needs an active account", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(managedAccountReview())
    .mockResolvedValueOnce(oidcBinding());
  const actions = createIdentityFeature({ request });
  const result = await actions.prepareOidcBinding.execute(access, accountId, bindingId, signal());
  expect(result).toMatchObject({
    ok: true,
    value: { account: { id: accountId }, binding: { id: bindingId, accountId, version: 3 } },
  });
  expect(request.mock.calls[1]?.[0].path).toBe(
    `/api/v1/identity/accounts/${accountId}/oidc/${bindingId}`,
  );
  request.mockClear().mockResolvedValue(managedAccountReview());
  expect(await actions.prepareOidcBinding.execute(access, accountId, null, signal())).toMatchObject(
    { ok: true, value: { binding: null } },
  );
  expect(request).toHaveBeenCalledOnce();
});
it("allows review to deactivate a binding of an inactive account but rejects new activation preparation", async () => {
  const review = managedAccountReview();
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce({ ...review, account: { ...review.account, active: false } })
    .mockResolvedValueOnce(oidcBinding());
  const actions = createIdentityFeature({ request });
  expect(
    await actions.prepareOidcBinding.execute(access, accountId, bindingId, signal()),
  ).toMatchObject({ ok: true, value: { account: { active: false }, binding: { active: true } } });
  request
    .mockClear()
    .mockResolvedValue({ ...review, account: { ...review.account, active: false } });
  expect(await actions.prepareOidcBinding.execute(access, accountId, null, signal())).toMatchObject(
    { ok: false, failure: { code: "account_disabled" } },
  );
  expect(request).toHaveBeenCalledOnce();
});
it.each([
  { items: [oidcBinding(), oidcBinding()], nextCursor: null },
  { items: [oidcBinding(2), oidcBinding(1)], nextCursor: null },
  { items: [oidcBinding()], nextCursor: oidcBindingId() },
  { items: [{ ...oidcBinding(), version: -1 }], nextCursor: null },
  { items: [{ ...oidcBinding(), linkedAt: "2026-99-01T00:00:00Z" }], nextCursor: null },
])("rejects inconsistent binding pages", async (page) => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(managedAccountReview())
    .mockResolvedValueOnce(page);
  expect(
    await createIdentityFeature({ request }).loadOidcBindings.execute(
      access,
      accountId,
      null,
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});
it.each([
  { id: oidcBindingId(2), version: 0 },
  { id: bindingId, version: 1 },
  { id: bindingId, version: -1 },
])("rejects an acknowledgement for another command", async (receipt) => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(receipt);
  expect(
    await createIdentityFeature({ request }).saveOidcBinding.execute(
      access,
      operation,
      input(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});
it("rejects an exact review for a different binding", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(managedAccountReview())
    .mockResolvedValueOnce(oidcBinding(2));
  expect(
    await createIdentityFeature({ request }).prepareOidcBinding.execute(
      access,
      accountId,
      bindingId,
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});
it.each([
  { subject: "bad\nsubject" },
  { issuer: "" },
  { subject: "x".repeat(256) },
  { reason: " " },
  { expectedVersion: Number.MAX_SAFE_INTEGER },
  { expectedVersion: -1 },
  { expectedVersion: 0.5 },
])("rejects malformed commands before transport", async (change) => {
  const request = vi.fn<HttpClient["request"]>();
  expect(
    await createIdentityFeature({ request }).saveOidcBinding.execute(
      access,
      operation,
      { ...input(), ...change },
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_oidc_identity" } });
  expect(request).not.toHaveBeenCalled();
});
it("rejects missing authority, unsafe paths and cancelled callers without acquiring data", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const actions = createIdentityFeature({ request });
  const denied = { ...access, permissions: [] };
  expect(await actions.loadOidcBindings.execute(denied, accountId, null, signal())).toMatchObject({
    ok: false,
    failure: { code: "platform_administrator_required" },
  });
  expect(
    await actions.prepareOidcBinding.execute(denied, accountId, bindingId, signal()),
  ).toMatchObject({ ok: false, failure: { code: "platform_administrator_required" } });
  expect(await actions.saveOidcBinding.execute(denied, operation, input(), signal())).toMatchObject(
    { ok: false, failure: { code: "platform_administrator_required" } },
  );
  expect(
    await actions.loadOidcBindings.execute(access, accountId, "../invalid", signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
  expect(
    await actions.prepareOidcBinding.execute(access, accountId, "../invalid", signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_identifier" } });
  expect(
    await actions.saveOidcBinding.execute(access, "invalid" as OperationId, input(), signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_operation_id" } });
  const stopped = new AbortController();
  stopped.abort();
  await expect(
    actions.loadOidcBindings.execute(access, accountId, null, stopped.signal),
  ).rejects.toMatchObject({ name: "AbortError" });
  expect(request).not.toHaveBeenCalled();
});
it("stops orchestration after account denial or cancellation and preserves safe server classifications", async () => {
  const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
    new HttpResponseError(403, {
      code: "platform_administrator_required",
      detail: "private provider internals",
    }),
  );
  const actions = createIdentityFeature({ request });
  expect(await actions.loadOidcBindings.execute(access, accountId, null, signal())).toEqual({
    ok: false,
    failure: { code: "platform_administrator_required", fields: {}, parameters: {} },
  });
  expect(request).toHaveBeenCalledOnce();
  const stopped = new AbortController();
  request.mockClear().mockImplementationOnce(async () => {
    stopped.abort();
    return managedAccountReview();
  });
  await expect(
    actions.prepareOidcBinding.execute(access, accountId, bindingId, stopped.signal),
  ).rejects.toMatchObject({ name: "AbortError" });
  expect(request).toHaveBeenCalledOnce();
});
