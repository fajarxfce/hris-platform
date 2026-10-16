import { expect, it, vi } from "vitest";
import {
  managedAccount,
  managedAccountId,
  managedAccountReview,
  platformActorId,
  platformPermissions,
} from "../../../../tests/fixtures/managed-accounts";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, OperationId } from "../../../core/domain/identifiers";
import type {
  ManagedAccountPageDto,
  ManagedAccountReviewDto,
} from "../data/models/managed-account-dto";
import type { AccountAccessChange } from "../domain/entities/account-access-change";
import { createIdentityFeature } from "./identity-feature";

const access = { accountId: platformActorId as AccountId, permissions: platformPermissions };
const operation = "d3000000-0000-4000-8000-000000000001" as OperationId;
const signal = () => new AbortController().signal;
const change = (): AccountAccessChange => ({
  accountId: managedAccountId() as AccountId,
  expectedVersion: 4,
  active: false,
  platformPermissions: ["companies.create"],
  reason: "Reviewed platform access",
});

it("uses explicit global authority and rejects invalid scope, cursors and identifiers before I/O", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  const denied = { ...access, permissions: [] };
  expect(await f.loadManagedAccounts.execute(denied, "", null, signal())).toMatchObject({
    ok: false,
    failure: { code: "platform_administrator_required" },
  });
  expect(await f.loadManagedAccount.execute(denied, managedAccountId(), signal())).toMatchObject({
    ok: false,
  });
  expect(await f.saveAccountAccess.execute(denied, operation, change(), signal())).toMatchObject({
    ok: false,
  });
  for (const [query, after] of [
    ["a".repeat(101), null],
    ["", "../other"],
  ])
    expect(
      await f.loadManagedAccounts.execute(access, query ?? "", after ?? null, signal()),
    ).toMatchObject({ ok: false });
  expect(await f.loadManagedAccount.execute(access, "../other", signal())).toMatchObject({
    ok: false,
  });
  expect(
    await f.loadManagedAccounts.execute(
      { ...access, accountId: "bad" as AccountId },
      "",
      null,
      signal(),
    ),
  ).toMatchObject({ ok: false });
  expect(
    await f.saveAccountAccess.execute(access, "bad" as OperationId, change(), signal()),
  ).toMatchObject({ ok: false });
  const cancelled = new AbortController();
  cancelled.abort();
  expect(() => f.loadManagedAccounts.execute(access, "", null, cancelled.signal)).toThrow();
  expect(() =>
    f.loadManagedAccount.execute(access, managedAccountId(), cancelled.signal),
  ).toThrow();
  expect(() =>
    f.saveAccountAccess.execute(access, operation, change(), cancelled.signal),
  ).toThrow();
  expect(request).not.toHaveBeenCalled();
});

it("reads one bounded page using normalized search and an advancing cursor", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ items: [managedAccount(2)], nextCursor: null });
  const result = await createIdentityFeature({ request }).loadManagedAccounts.execute(
    access,
    "  COLLEAGUE  ",
    managedAccountId(),
    signal(),
  );
  expect(result.ok).toBe(true);
  expect(request).toHaveBeenCalledTimes(1);
  const path = new URL(request.mock.calls[0]?.[0].path ?? "", "https://fixture.invalid");
  expect(path.pathname).toBe("/api/v1/identity/accounts");
  expect(Object.fromEntries(path.searchParams)).toEqual({
    query: "colleague",
    after: managedAccountId(),
    limit: "50",
  });
  if (result.ok) {
    expect(Object.isFrozen(result.value.items)).toBe(true);
    expect(Object.isFrozen(result.value.items[0]?.platformPermissions)).toBe(true);
  }
});

it.each<ManagedAccountPageDto>([
  { items: [managedAccount(1), managedAccount(1)], nextCursor: null },
  { items: [managedAccount(2), managedAccount(1)], nextCursor: null },
  { items: [managedAccount(1)], nextCursor: managedAccountId() },
  { items: [], nextCursor: managedAccountId() },
  { items: Array.from({ length: 51 }, (_, i) => managedAccount(i + 1)), nextCursor: null },
])("rejects malformed or non-progressing account pages: %j", async (raw) => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
  expect(
    await createIdentityFeature({ request }).loadManagedAccounts.execute(
      access,
      "",
      null,
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});

it("accepts a full page cursor but rejects rows before the requested position", async () => {
  const rows = Array.from({ length: 50 }, (_, i) => managedAccount(i + 1));
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ items: rows, nextCursor: managedAccountId(50) });
  const f = createIdentityFeature({ request });
  expect(await f.loadManagedAccounts.execute(access, "", null, signal())).toMatchObject({
    ok: true,
    value: { nextCursor: managedAccountId(50) },
  });
  expect(
    await f.loadManagedAccounts.execute(access, "", managedAccountId(1), signal()),
  ).toMatchObject({ ok: false });
});

it.each<Partial<ManagedAccountReviewDto>>([
  { account: { ...managedAccount(), id: managedAccountId(2) } },
  { account: { ...managedAccount(), active: true, invitationPending: true } },
  { account: { ...managedAccount(), version: 4.5 } },
  { account: { ...managedAccount(), version: Number.MAX_SAFE_INTEGER + 1 } },
  { account: { ...managedAccount(), platformPermissions: ["identity.manage", "identity.manage"] } },
  { assignablePermissions: ["identity.manage", "identity.manage"] },
])("validates exact account review and bounded permission metadata: %j", async (patch) => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ ...managedAccountReview(), ...patch });
  expect(
    await createIdentityFeature({ request }).loadManagedAccount.execute(
      access,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});

it("detaches review data and never carries unrecognized credential fields into domain", async () => {
  const raw = {
    ...managedAccountReview(),
    internal: "private",
    account: { ...managedAccount(), passwordHash: "private", authenticator: "private" },
  };
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
  const result = await createIdentityFeature({ request }).loadManagedAccount.execute(
    access,
    managedAccountId().toUpperCase(),
    signal(),
  );
  expect(result.ok).toBe(true);
  raw.account.platformPermissions.length = 0;
  raw.assignablePermissions.length = 0;
  expect(JSON.stringify(result)).not.toContain("private");
  if (result.ok) {
    expect(result.value.account.platformPermissions).toEqual(["companies.create"]);
    expect(result.value.assignablePermissions).toEqual(platformPermissions);
    expect(Object.isFrozen(result.value.account)).toBe(true);
  }
});

it.each<Partial<AccountAccessChange>>([
  { accountId: "bad" as AccountId },
  { expectedVersion: -1 },
  { expectedVersion: 0.5 },
  { expectedVersion: Number.MAX_SAFE_INTEGER },
  { active: null as unknown as boolean },
  { reason: " " },
  { reason: "a".repeat(1001) },
  { platformPermissions: ["not valid"] },
  { platformPermissions: ["identity.manage", "identity.manage"] },
  { platformPermissions: Array.from({ length: 101 }, (_, i) => `fixture.p${i}`) },
])("validates account access input before dispatch: %j", async (patch) => {
  const request = vi.fn<HttpClient["request"]>();
  expect(
    await createIdentityFeature({ request }).saveAccountAccess.execute(
      access,
      operation,
      { ...change(), ...patch },
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_account_access" } });
  expect(request).not.toHaveBeenCalled();
});

it("protects the caller's own administrator status before sending a command", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  for (const input of [
    { ...change(), accountId: access.accountId },
    { ...change(), accountId: access.accountId, active: true },
  ])
    expect(await f.saveAccountAccess.execute(access, operation, input, signal())).toMatchObject({
      ok: false,
      failure: { code: "cannot_remove_own_administration" },
    });
  expect(request).not.toHaveBeenCalled();
});

it("sends the detached reviewed account version with one normalized operation", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ id: managedAccountId(), version: 5 });
  const input = {
    ...change(),
    accountId: managedAccountId().toUpperCase() as AccountId,
    platformPermissions: ["identity.manage", "companies.create"],
    reason: "  Reviewed access  ",
  };
  const result = await createIdentityFeature({ request }).saveAccountAccess.execute(
    access,
    operation.toUpperCase() as OperationId,
    input,
    signal(),
  );
  input.platformPermissions.length = 0;
  expect(result).toEqual({ ok: true, value: { id: managedAccountId(), version: 5 } });
  expect(request.mock.lastCall?.[0]).toEqual({
    path: `/api/v1/identity/accounts/${managedAccountId()}/access`,
    method: "PUT",
    operationId: operation,
    body: { expectedVersion: 4, active: false, platformPermissions, reason: "Reviewed access" },
  });
});

it.each([
  { id: managedAccountId(2), version: 5 },
  { id: managedAccountId(), version: 4 },
  { id: managedAccountId(), version: 6 },
])(
  "rejects a receipt that does not acknowledge the original account command: %j",
  async (receipt) => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(receipt);
    expect(
      await createIdentityFeature({ request }).saveAccountAccess.execute(
        access,
        operation,
        change(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  },
);

it("maps safe account failure codes and propagates cancellation after response acquisition", async () => {
  const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
    new HttpResponseError(409, {
      code: "invitation_acceptance_required",
      detail: "private provider details",
      fields: {},
      parameters: {},
    }),
  );
  const f = createIdentityFeature({ request });
  const result = await f.saveAccountAccess.execute(access, operation, change(), signal());
  expect(result).toMatchObject({ ok: false, failure: { code: "invitation_acceptance_required" } });
  expect(JSON.stringify(result)).not.toContain("private");
  let resolve!: (value: unknown) => void;
  request.mockReturnValueOnce(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const cancellation = new AbortController();
  const saving = f.saveAccountAccess.execute(access, operation, change(), cancellation.signal);
  cancellation.abort();
  resolve({ id: managedAccountId(), version: 5 });
  await expect(saving).rejects.toMatchObject({ name: "AbortError" });
});
