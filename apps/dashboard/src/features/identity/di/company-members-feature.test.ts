import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createIdentityFeature } from "./identity-feature";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["identity.manage"],
};
const member = {
  id: "20000000-0000-4000-8000-000000000001",
  email: "member@example.invalid",
  displayName: "Example Member",
  accountActive: true,
  active: false,
  permissions: ["people.self.read"],
  version: 3,
};
const grant = {
  member,
  directPermissions: [],
  roleTemplates: [
    {
      id: "30000000-0000-4000-8000-000000000001",
      code: "EMPLOYEE",
      name: "Employee",
      permissions: member.permissions,
      version: 2,
    },
  ],
};
const signal = () => new AbortController().signal;

describe("company membership API boundaries", () => {
  it("validates scope and identifiers before requesting private membership data", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createIdentityFeature({ request });
    expect(
      await feature.loadCompanyMembers.execute({ ...access, permissions: [] }, null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await feature.loadCompanyMembers.execute(access, "../other", signal())).toMatchObject({
      ok: false,
    });
    expect(await feature.loadCompanyMember.execute(access, "../other", signal())).toMatchObject({
      ok: false,
    });
    expect(request).not.toHaveBeenCalled();
  });
  it("retains immutable pages and applied role snapshots through scoped endpoints", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ items: [member], nextCursor: member.id })
      .mockResolvedValueOnce(grant);
    const feature = createIdentityFeature({ request });
    const page = await feature.loadCompanyMembers.execute(access, null, signal());
    expect(request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${access.companyId}/members?limit=50`,
    );
    if (!page.ok) throw new Error("Invalid fixture");
    expect(Object.isFrozen(page.value.items)).toBe(true);
    expect(Object.isFrozen(page.value.items[0]?.permissions)).toBe(true);
    const details = await feature.loadCompanyMember.execute(access, member.id, signal());
    if (!details.ok) throw new Error("Invalid fixture");
    expect(details.value.member.version).toBe(3);
    expect(details.value.member.membershipActive).toBe(false);
    expect(details.value.member.accountActive).toBe(true);
    expect(details.value.roleTemplates[0]?.version).toBe(2);
    expect(Object.isFrozen(details.value.roleTemplates[0]?.permissions)).toBe(true);
    expect(request.mock.calls[1]?.[0].path).toBe(
      `/api/v1/companies/${access.companyId}/members/${member.id}`,
    );
  });
  it("rejects repeated pages, duplicate identities, wrong details and unbounded DTOs", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createIdentityFeature({ request });
    for (const page of [
      { items: [member, member], nextCursor: null },
      { items: [], nextCursor: member.id },
      { items: [member], nextCursor: "20000000-0000-4000-8000-000000000009" },
      {
        items: Array.from({ length: 51 }, (_, index) => ({
          ...member,
          id: `20000000-0000-4000-8000-${index.toString().padStart(12, "0")}`,
        })),
        nextCursor: null,
      },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(await feature.loadCompanyMembers.execute(access, null, signal())).toMatchObject({
        ok: false,
      });
    }
    request.mockResolvedValueOnce({ items: [member], nextCursor: member.id });
    expect(await feature.loadCompanyMembers.execute(access, member.id, signal())).toMatchObject({
      ok: false,
    });
    request.mockResolvedValueOnce(grant);
    expect(
      await feature.loadCompanyMember.execute(
        access,
        "20000000-0000-4000-8000-000000000002",
        signal(),
      ),
    ).toMatchObject({ ok: false });
  });
  it("preserves domain problem codes and cancellation without retrying or exposing diagnostics", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(
        new HttpResponseError(403, { code: "company_access_denied", detail: "private diagnostic" }),
      );
    const feature = createIdentityFeature({ request });
    const result = await feature.loadCompanyMembers.execute(access, null, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "company_access_denied" } });
    expect(JSON.stringify(result)).not.toContain("private");
    const cancelled = new AbortController();
    request.mockImplementationOnce(async () => {
      cancelled.abort();
      return { items: [member], nextCursor: null };
    });
    await expect(
      feature.loadCompanyMembers.execute(access, null, cancelled.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(2);
  });
});
