import { expect, it, vi } from "vitest";
import {
  companyMembershipGrant,
  companyMembershipId,
} from "../../../../tests/fixtures/company-memberships";
import { roleTemplateId } from "../../../../tests/fixtures/role-templates";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { CompanyMembershipChange } from "../domain/entities/company-membership-change";
import { createIdentityFeature } from "./identity-feature";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["identity.manage"],
};
const operation = "d2000000-0000-4000-8000-000000000001" as OperationId;
const signal = () => new AbortController().signal;
const change = (): CompanyMembershipChange => ({
  accountId: companyMembershipId,
  expectedVersion: 4,
  active: true,
  permissions: ["people.read", "company.read"],
  roleTemplates: [{ id: roleTemplateId(), version: 0, retainApplied: true }],
  reason: "Reviewed responsibilities",
});

it("rejects invalid authority, identifiers and pre-cancellation before membership I/O", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  expect(
    await f.saveCompanyMembership.execute(
      { ...access, permissions: [] },
      operation,
      change(),
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
  expect(
    await f.saveCompanyMembership.execute(
      { ...access, companyId: "../other" as CompanyId },
      operation,
      change(),
      signal(),
    ),
  ).toMatchObject({ ok: false });
  expect(
    await f.saveCompanyMembership.execute(access, "wrong" as OperationId, change(), signal()),
  ).toMatchObject({ ok: false });
  const cancelled = new AbortController();
  cancelled.abort();
  expect(() =>
    f.saveCompanyMembership.execute(access, operation, change(), cancelled.signal),
  ).toThrow();
  expect(request).not.toHaveBeenCalled();
});

it.each<Partial<CompanyMembershipChange>>([
  { accountId: "../other" },
  { expectedVersion: -1 },
  { expectedVersion: 0.5 },
  { expectedVersion: Number.MAX_SAFE_INTEGER },
  { active: null as unknown as boolean },
  { reason: "  " },
  { reason: "a".repeat(1001) },
  { permissions: ["not valid"] },
  { permissions: ["people.read", "people.read"] },
  { permissions: Array.from({ length: 201 }, (_, i) => `fixture.p${i}`) },
  {
    roleTemplates: Array.from({ length: 9 }, (_, i) => ({
      id: roleTemplateId(i),
      version: 0,
      retainApplied: false,
    })),
  },
  {
    roleTemplates: [
      { id: roleTemplateId(), version: 0, retainApplied: false },
      { id: roleTemplateId().toUpperCase(), version: 0, retainApplied: true },
    ],
  },
  { roleTemplates: [{ id: "../other", version: 0, retainApplied: false }] },
  { roleTemplates: [{ id: roleTemplateId(), version: 0.5, retainApplied: false }] },
  {
    roleTemplates: [
      { id: roleTemplateId(), version: 0, retainApplied: "false" as unknown as boolean },
    ],
  },
])("bounds membership input before dispatch: %j", async (patch) => {
  const request = vi.fn<HttpClient["request"]>();
  const result = await createIdentityFeature({ request }).saveCompanyMembership.execute(
    access,
    operation,
    { ...change(), ...patch },
    signal(),
  );
  expect(result).toMatchObject({ ok: false, failure: { code: "invalid_membership" } });
  expect(request).not.toHaveBeenCalled();
});

it("detaches and normalizes the reviewed grant while preserving role intent and one operation", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ id: companyMembershipId, version: 5 });
  const input = {
    ...change(),
    accountId: companyMembershipId.toUpperCase(),
    reason: "  Reviewed access  ",
    roleTemplates: [
      { id: roleTemplateId(2).toUpperCase(), version: 3, retainApplied: false },
      { id: roleTemplateId().toUpperCase(), version: 0, retainApplied: true },
    ],
  };
  const result = await createIdentityFeature({ request }).saveCompanyMembership.execute(
    access,
    operation,
    input,
    signal(),
  );
  expect(result).toEqual({ ok: true, value: { id: companyMembershipId, version: 5 } });
  expect(request.mock.lastCall?.[0]).toEqual({
    path: `/api/v1/companies/${access.companyId}/members/${companyMembershipId}`,
    method: "PUT",
    operationId: operation,
    body: {
      expectedVersion: 4,
      active: true,
      permissions: ["company.read", "people.read"],
      reason: "Reviewed access",
      roleTemplates: [
        { id: roleTemplateId(), version: 0, retainApplied: true },
        { id: roleTemplateId(2), version: 3, retainApplied: false },
      ],
    },
  });
  const [first] = input.roleTemplates;
  if (!first) throw new Error("Expected the selected role fixture");
  first.id = roleTemplateId(9);
  expect(request.mock.lastCall?.[0].body).toMatchObject({
    roleTemplates: [{ id: roleTemplateId() }, { id: roleTemplateId(2) }],
  });
  if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
});

it.each([
  { id: roleTemplateId(), version: 5 },
  { id: companyMembershipId, version: 4 },
  { id: companyMembershipId, version: 6 },
  { id: companyMembershipId, version: 5.5 },
])("rejects an inconsistent membership receipt: %j", async (receipt) => {
  const request = vi.fn<HttpClient["request"]>().mockResolvedValue(receipt);
  const result = await createIdentityFeature({ request }).saveCompanyMembership.execute(
    access,
    operation,
    change(),
    signal(),
  );
  expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});

it("maps technical failures without exposing server details", async () => {
  const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
    new HttpResponseError(409, {
      code: "stale_role_template",
      detail: "PRIVATE SERVER TEXT",
      fields: {},
      parameters: {},
    }),
  );
  const result = await createIdentityFeature({ request }).saveCompanyMembership.execute(
    access,
    operation,
    change(),
    signal(),
  );
  expect(result).toMatchObject({ ok: false, failure: { code: "stale_role_template" } });
  expect(JSON.stringify(result)).not.toContain("PRIVATE SERVER TEXT");
});

it("requires membership permissions to match their recorded direct and role sources", async () => {
  const raw = companyMembershipGrant();
  const [role] = raw.roleTemplates;
  if (!role) throw new Error("Expected the applied role fixture");
  const request = vi.fn<HttpClient["request"]>();
  const f = createIdentityFeature({ request });
  for (const invalid of [
    { ...raw, directPermissions: [] },
    { ...raw, member: { ...raw.member, permissions: ["company.read", "payroll.read"] } },
    {
      ...raw,
      roleTemplates: Array.from({ length: 9 }, (_, index) => ({
        ...role,
        id: roleTemplateId(index),
      })),
    },
  ]) {
    request.mockResolvedValueOnce(invalid);
    expect(await f.loadCompanyMember.execute(access, companyMembershipId, signal())).toMatchObject({
      ok: false,
    });
  }
  request.mockResolvedValueOnce(raw);
  const result = await f.loadCompanyMember.execute(access, companyMembershipId, signal());
  expect(result.ok).toBe(true);
  raw.member.permissions.length = 0;
  if (result.ok)
    expect(result.value.member.permissions).toEqual(["company.read", "people.team.read"]);
});

it("propagates cancellation after a pending membership response instead of returning its receipt", async () => {
  let resolve!: (value: unknown) => void;
  const pending = new Promise<unknown>((done) => {
    resolve = done;
  });
  const request = vi.fn<HttpClient["request"]>().mockReturnValue(pending);
  const cancellation = new AbortController();
  const result = createIdentityFeature({ request }).saveCompanyMembership.execute(
    access,
    operation,
    change(),
    cancellation.signal,
  );
  cancellation.abort();
  resolve({ id: companyMembershipId, version: 5 });
  await expect(result).rejects.toMatchObject({ name: "AbortError" });
});
