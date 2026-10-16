import { expect, it, vi } from "vitest";
import { companyMembershipGrant } from "../../../../tests/fixtures/company-memberships";
import {
  managedAccountId,
  managedAccountReview,
  platformActorId,
} from "../../../../tests/fixtures/managed-accounts";
import { roleCatalog, roleTemplateId } from "../../../../tests/fixtures/role-templates";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { CompanyAccountAccess } from "../domain/entities/company-account-access";
import type { CompanyMembershipAssignment } from "../domain/entities/company-membership-assignment";
import { createIdentityFeature } from "./identity-feature";

const access: CompanyAccountAccess = {
  company: {
    companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
    permissions: ["identity.manage"],
  },
  platform: { accountId: platformActorId as AccountId, permissions: ["identity.manage"] },
};
const operation = "d2000000-0000-4000-8000-000000000003" as OperationId;
const signal = () => new AbortController().signal;
const assignment = (): CompanyMembershipAssignment => ({
  accountId: managedAccountId(),
  permissions: ["leave.read", "company.read"],
  roleTemplates: [{ id: roleTemplateId(), version: 2, retainApplied: false }],
  reason: "Reviewed initial access",
});
const absent = () => new HttpResponseError(404, { code: "company_member_not_found" });
function reviewFixture() {
  const request = vi.fn<HttpClient["request"]>().mockImplementation(async ({ path }) => {
    if (path.endsWith(`/members/${managedAccountId()}`)) throw absent();
    if (path === `/api/v1/identity/accounts/${managedAccountId()}`) return managedAccountReview();
    if (path.endsWith("/role-templates/permissions")) return roleCatalog;
    throw new Error("Unexpected fixture request");
  });
  return { request, feature: createIdentityFeature({ request }) };
}

it("reviews absence, active account and current company permissions without creating membership", async () => {
  const f = reviewFixture();
  const result = await f.feature.prepareCompanyMembership.execute(
    access.company,
    access.platform,
    managedAccountId().toUpperCase(),
    signal(),
  );
  expect(result).toEqual({
    ok: true,
    value: {
      accountId: managedAccountId(),
      displayName: "Example Colleague 1",
      email: "colleague1@example.invalid",
      assignablePermissions: roleCatalog.toSorted(),
    },
  });
  expect(f.request.mock.calls.map(([request]) => request)).toEqual([
    { path: `/api/v1/companies/${access.company.companyId}/members/${managedAccountId()}` },
    { path: `/api/v1/identity/accounts/${managedAccountId()}` },
    { path: `/api/v1/companies/${access.company.companyId}/role-templates/permissions` },
  ]);
  if (!result.ok) throw new Error("Expected prepared account");
  expect(Object.isFrozen(result.value)).toBe(true);
  expect(Object.isFrozen(result.value.assignablePermissions)).toBe(true);
});

it.each([true, false])(
  "existing company access (active=%s) must use the existing editor",
  async (active) => {
    const f = reviewFixture();
    const grant = companyMembershipGrant();
    grant.member = { ...grant.member, id: managedAccountId(), active };
    f.request.mockResolvedValueOnce(grant);
    expect(
      await f.feature.prepareCompanyMembership.execute(
        access.company,
        access.platform,
        managedAccountId(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "company_member_already_exists" } });
    expect(f.request).toHaveBeenCalledTimes(1);
  },
);

it.each(["access_denied", "company_access_denied", "mfa_required", "request_timeout"])(
  "preserves %s rather than treating it as an absent member",
  async (code) => {
    const f = reviewFixture();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(403, { code, detail: "private technical details" }),
    );
    expect(
      await f.feature.prepareCompanyMembership.execute(
        access.company,
        access.platform,
        managedAccountId(),
        signal(),
      ),
    ).toEqual({ ok: false, failure: { code, fields: {}, parameters: {} } });
    expect(f.request).toHaveBeenCalledTimes(1);
  },
);

it.each([
  { active: false, invitationPending: false },
  { active: false, invitationPending: true },
])("rejects an unavailable account before acquiring grants: %j", async (patch) => {
  const f = reviewFixture();
  const review = managedAccountReview();
  f.request
    .mockRejectedValueOnce(absent())
    .mockResolvedValueOnce({ ...review, account: { ...review.account, ...patch } });
  expect(
    await f.feature.prepareCompanyMembership.execute(
      access.company,
      access.platform,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "account_unavailable" } });
  expect(f.request).toHaveBeenCalledTimes(2);
});

it("does not publish partial preparation when the permission read fails", async () => {
  const f = reviewFixture();
  f.request
    .mockRejectedValueOnce(absent())
    .mockResolvedValueOnce(managedAccountReview())
    .mockRejectedValueOnce(new HttpResponseError(403, { code: "mfa_required" }));
  expect(
    await f.feature.prepareCompanyMembership.execute(
      access.company,
      access.platform,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
  expect(f.request).toHaveBeenCalledTimes(3);
});

it.each(["member", "account", "permissions"])(
  "cancellation during %s acquisition stops further work and rejects its late result",
  async (stage) => {
    const f = reviewFixture();
    const pending = new AbortController();
    f.request.mockImplementation(async ({ path }) => {
      if (path.includes("/members/")) {
        if (stage === "member") pending.abort();
        throw absent();
      }
      if (path.includes("/identity/accounts/")) {
        if (stage === "account") pending.abort();
        return managedAccountReview();
      }
      pending.abort();
      return roleCatalog;
    });
    await expect(
      f.feature.prepareCompanyMembership.execute(
        access.company,
        access.platform,
        managedAccountId(),
        pending.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.request).toHaveBeenCalledTimes(stage === "member" ? 1 : stage === "account" ? 2 : 3);
  },
);

it.each([
  { scope: { ...access, company: { ...access.company, permissions: [] } }, code: "access_denied" },
  {
    scope: { ...access, platform: { ...access.platform, permissions: [] } },
    code: "platform_administrator_required",
  },
  {
    scope: { ...access, company: { ...access.company, companyId: "other" as CompanyId } },
    code: "company_required",
  },
])("requires both discovery scopes before I/O: $code", async ({ scope, code }) => {
  const f = reviewFixture();
  expect(
    await f.feature.loadCompanyAccountCandidates.execute(scope, "", null, signal()),
  ).toMatchObject({ ok: false, failure: { code } });
  expect(
    await f.feature.prepareCompanyMembership.execute(
      scope.company,
      scope.platform,
      managedAccountId(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code } });
  expect(f.request).not.toHaveBeenCalled();
});

it("bounds explicit candidate search and retains normalized cursor context", async () => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ items: [], nextCursor: null });
  const f = createIdentityFeature({ request });
  for (const [query, after] of [
    ["x".repeat(101), null],
    ["", "not-a-cursor"],
  ] as const)
    expect(
      await f.loadCompanyAccountCandidates.execute(access, query, after, signal()),
    ).toMatchObject({ ok: false });
  expect(request).not.toHaveBeenCalled();
  await f.loadCompanyAccountCandidates.execute(
    access,
    "  Colleague  ",
    managedAccountId().toUpperCase(),
    signal(),
  );
  const url = new URL(request.mock.lastCall?.[0].path ?? "", "https://fixture.invalid");
  expect(url.pathname).toBe("/api/v1/identity/accounts");
  expect(Object.fromEntries(url.searchParams)).toEqual({
    query: "colleague",
    after: managedAccountId(),
    limit: "50",
  });
});

it("creates only absent active access, detaches reviewed input and accepts its original version-zero receipt", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ id: managedAccountId(), version: 0, replayed: true });
  const f = createIdentityFeature({ request });
  const input = {
    ...assignment(),
    accountId: managedAccountId().toUpperCase(),
    reason: "  Reviewed initial access  ",
    active: false,
    expectedVersion: 99,
  };
  expect(
    await f.createCompanyMembership.execute(access.company, operation, input, signal()),
  ).toEqual({ ok: true, value: { id: managedAccountId(), version: 0 } });
  expect(request.mock.lastCall?.[0]).toEqual({
    path: `/api/v1/companies/${access.company.companyId}/members/${managedAccountId()}`,
    method: "PUT",
    operationId: operation,
    body: {
      expectedVersion: null,
      active: true,
      permissions: ["company.read", "leave.read"],
      roleTemplates: assignment().roleTemplates,
      reason: "Reviewed initial access",
    },
  });
  expect(input.permissions).toEqual(["leave.read", "company.read"]);
  expect(request).toHaveBeenCalledTimes(1);
});

it.each<Partial<CompanyMembershipAssignment>>([
  { reason: " " },
  { reason: "x".repeat(1001) },
  { accountId: "../other" },
  { permissions: ["leave.read", "leave.read"] },
  { roleTemplates: [{ id: roleTemplateId(), version: 1.5, retainApplied: false }] },
  {
    roleTemplates: Array.from({ length: 9 }, (_, i) => ({
      id: roleTemplateId(i),
      version: 0,
      retainApplied: false,
    })),
  },
])("rejects invalid assignment terms before dispatch: %j", async (patch) => {
  const request = vi.fn<HttpClient["request"]>();
  expect(
    await createIdentityFeature({ request }).createCompanyMembership.execute(
      access.company,
      operation,
      { ...assignment(), ...patch },
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_membership" } });
  expect(request).not.toHaveBeenCalled();
});

it("cannot retain another membership's applied role and cannot create without company authority", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  expect(
    await f.createCompanyMembership.execute(
      access.company,
      operation,
      {
        ...assignment(),
        roleTemplates: [{ id: roleTemplateId(), version: 0, retainApplied: true }],
      },
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "role_template_review_required" } });
  expect(
    await f.createCompanyMembership.execute(
      { ...access.company, permissions: [] },
      operation,
      assignment(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
  expect(
    await f.createCompanyMembership.execute(
      access.company,
      "invalid" as OperationId,
      assignment(),
      signal(),
    ),
  ).toMatchObject({ ok: false });
  expect(request).not.toHaveBeenCalled();
});

it.each([
  { id: managedAccountId(), version: 1 },
  { id: managedAccountId(2), version: 0 },
])("rejects a receipt for another membership or a noninitial version: %j", async (receipt) => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(receipt);
  expect(
    await createIdentityFeature({ request }).createCompanyMembership.execute(
      access.company,
      operation,
      assignment(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});

it("preserves a concurrent membership conflict and never performs a hidden retry", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockRejectedValue(new HttpResponseError(409, { code: "stale_version" }));
  expect(
    await createIdentityFeature({ request }).createCompanyMembership.execute(
      access.company,
      operation,
      assignment(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "stale_version" } });
  expect(request).toHaveBeenCalledTimes(1);
});

it("rejects acquisition and creation that were already cancelled", async () => {
  const f = reviewFixture();
  const pending = new AbortController();
  pending.abort();
  await expect(
    f.feature.prepareCompanyMembership.execute(
      access.company,
      access.platform,
      managedAccountId(),
      pending.signal,
    ),
  ).rejects.toMatchObject({ name: "AbortError" });
  expect(() =>
    f.feature.createCompanyMembership.execute(
      access.company,
      operation,
      assignment(),
      pending.signal,
    ),
  ).toThrow();
  expect(() =>
    f.feature.loadCompanyAccountCandidates.execute(access, "", null, pending.signal),
  ).toThrow();
  expect(f.request).not.toHaveBeenCalled();
});
