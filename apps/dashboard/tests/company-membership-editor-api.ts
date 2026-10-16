import { expect, type Page } from "@playwright/test";
import type { CompanyMemberGrantDto } from "../src/features/identity/data/models/company-member-dto";
import type { CompanyMembershipChangeDto } from "../src/features/identity/data/models/company-membership-change-dto";
import { companyMembershipGrant, companyMembershipId } from "./fixtures/company-memberships";
import { companyIds } from "./identity-api";
import { installRoleTemplatesApi } from "./role-templates-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}
export async function installCompanyMembershipEditorApi(
  page: Page,
  options: { allowed?: boolean; longPage?: boolean } = {},
) {
  const roles = await installRoleTemplatesApi(page, options);
  const other = companyMembershipGrant();
  other.member = {
    ...other.member,
    id: "21000000-0000-4000-8000-000000000002",
    displayName: "Other colleague",
    email: "other@example.invalid",
  };
  const grants = new Map<string, CompanyMemberGrantDto>([
    [companyIds[0], companyMembershipGrant()],
    [companyIds[1], other],
  ]);
  const reads: URL[] = [];
  const writes: {
    company: string;
    account: string;
    operation: string;
    body: CompanyMembershipChangeDto;
  }[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let rejected: string | null = null;
  let readFailure: string | null = null;
  let dropped = false;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldSave: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  await page.route("**/api/v1/companies/*/members**", async (route) => {
    const url = new URL(route.request().url());
    const match = /^\/api\/v1\/companies\/([^/]+)\/members(?:\/([^/]+))?$/u.exec(url.pathname);
    if (!match) return route.fallback();
    const company = match[1] ?? "";
    const account = match[2];
    const grant = grants.get(company);
    if (route.request().method() === "PUT" && account) {
      const operation = route.request().headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      const body = route.request().postDataJSON() as CompanyMembershipChangeDto;
      writes.push({ company, account, operation, body });
      const held = heldSave;
      heldSave = null;
      if (held) {
        held.entered.release();
        await held.released.promise;
      }
      if (rejected) {
        const code = rejected;
        rejected = null;
        return route.fulfill({ status: code === "mfa_required" ? 403 : 409, json: { code } });
      }
      const saved = receipts.get(`${company}:${operation}`);
      if (saved) {
        expect(saved.id).toBe(account);
        expect(JSON.stringify(body)).toBe(saved.payload);
        return route.fulfill({ json: { id: saved.id, version: saved.version } });
      }
      if (!grant || account !== grant.member.id || body.expectedVersion !== grant.member.version)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const selected: CompanyMemberGrantDto["roleTemplates"] = [];
      for (const selection of body.roleTemplates) {
        const role = selection.retainApplied
          ? grant.roleTemplates.find(
              (value) => value.id === selection.id && value.version === selection.version,
            )
          : roles.records
              .get(company)
              ?.find(
                (value) =>
                  value.current.id === selection.id &&
                  value.current.version === selection.version &&
                  value.current.active,
              )?.current;
        if (!role) return route.fulfill({ status: 409, json: { code: "stale_role_template" } });
        selected.push({
          id: role.id,
          code: role.code,
          name: role.name,
          version: role.version,
          permissions: [...role.permissions],
        });
      }
      const version = grant.member.version + 1;
      grants.set(company, {
        member: {
          ...grant.member,
          active: body.active,
          version,
          permissions: [
            ...new Set([...body.permissions, ...selected.flatMap((role) => role.permissions)]),
          ].sort(),
        },
        directPermissions: [...body.permissions],
        roleTemplates: selected,
      });
      receipts.set(`${company}:${operation}`, {
        id: account,
        version,
        payload: JSON.stringify(body),
      });
      if (dropped) {
        dropped = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: account, version } });
    }
    reads.push(url);
    const held = heldRead;
    heldRead = null;
    if (held) {
      held.entered.release();
      await held.released.promise;
    }
    if (readFailure)
      return route.fulfill({
        status: 404,
        json: { code: readFailure, detail: "PRIVATE MEMBER DETAIL" },
      });
    if (!grant || (account && account !== grant.member.id))
      return route.fulfill({ status: 404, json: { code: "company_member_not_found" } });
    return route.fulfill({ json: account ? grant : { items: [grant.member], nextCursor: null } });
  });
  return {
    roles,
    grants,
    reads,
    writes,
    dropNext: () => {
      dropped = true;
    },
    rejectNext: (code: string) => {
      rejected = code;
    },
    failReads: (code: string | null) => {
      readFailure = code;
    },
    advance: () => {
      const grant = grants.get(companyIds[0]);
      if (grant) grant.member.version++;
    },
    holdRead: () => {
      const entered = gate(),
        released = gate();
      heldRead = { entered, released };
      return { entered: entered.promise, release: released.release };
    },
    holdSave: () => {
      const entered = gate(),
        released = gate();
      heldSave = { entered, released };
      return { entered: entered.promise, release: released.release };
    },
    editor: `/administration/members/${companyMembershipId}/edit`,
  };
}
