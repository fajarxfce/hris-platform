import { expect, type Page } from "@playwright/test";
import type { CompanyMemberGrantDto } from "../src/features/identity/data/models/company-member-dto";
import type { CompanyMembershipChangeDto } from "../src/features/identity/data/models/company-membership-change-dto";
import { companyMembershipGrant } from "./fixtures/company-memberships";
import {
  managedAccount,
  managedAccountId,
  platformActorId,
  platformPermissions,
} from "./fixtures/managed-accounts";
import { companyIds } from "./identity-api";
import { installRoleTemplatesApi } from "./role-templates-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}
export async function installCompanyMembershipCreationApi(
  page: Page,
  options: { platform?: boolean; company?: boolean; longPage?: boolean } = {},
) {
  const roles = await installRoleTemplatesApi(page, {
    allowed: options.company ?? true,
    longPage: true,
  });
  const identity = roles.identity;
  identity.setPlatformPermissions(options.platform === false ? [] : platformPermissions);
  const accounts = new Map(
    Array.from({ length: options.longPage ? 51 : 4 }, (_, index) => {
      const record = managedAccount(index + 1);
      return [record.id, record] as const;
    }),
  );
  accounts.set(managedAccountId(2), { ...managedAccount(2), active: false });
  accounts.set(managedAccountId(3), {
    ...managedAccount(3),
    active: false,
    invitationPending: true,
  });
  accounts.set(platformActorId, {
    ...managedAccount(),
    id: platformActorId,
    displayName: "Sample Reviewer",
    email: "reviewer@example.invalid",
  });
  const memberships = new Map<string, Map<string, CompanyMemberGrantDto>>(
    companyIds.map((company) => [company, new Map()]),
  );
  const reads: URL[] = [];
  const writes: {
    company: string;
    account: string;
    operation: string;
    body: CompanyMembershipChangeDto;
  }[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let drop = false;
  let rejected: string | null = null;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldSave: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;

  await page.route("**/api/v1/identity/accounts**", async (route) => {
    const url = new URL(route.request().url());
    reads.push(url);
    const match = /^\/api\/v1\/identity\/accounts(?:\/([^/]+))?$/u.exec(url.pathname);
    if (!match) return route.fallback();
    if (match[1]) {
      const account = accounts.get(match[1]);
      return account
        ? route.fulfill({ json: { account, assignablePermissions: platformPermissions } })
        : route.fulfill({ status: 404, json: { code: "account_not_found" } });
    }
    const query = url.searchParams.get("query") ?? "";
    const after = url.searchParams.get("after") ?? "";
    const remaining = [...accounts.values()]
      .filter(
        (account) =>
          account.id > after &&
          `${account.displayName} ${account.email}`.toLowerCase().includes(query),
      )
      .sort((a, b) => a.id.localeCompare(b.id));
    const items = remaining.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: remaining.length > items.length ? items.at(-1)?.id : null },
    });
  });
  await page.route("**/api/v1/companies/*/members**", async (route) => {
    const url = new URL(route.request().url());
    const match = /^\/api\/v1\/companies\/([^/]+)\/members(?:\/([^/]+))?$/u.exec(url.pathname);
    if (!match) return route.fallback();
    const company = match[1] ?? "";
    const account = match[2];
    const members = memberships.get(company);
    if (!members) return route.fulfill({ status: 403, json: { code: "company_access_denied" } });
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
      const previous = receipts.get(`${company}:${operation}`);
      if (previous) {
        expect(previous.payload).toBe(JSON.stringify(body));
        expect(previous.id).toBe(account);
        return route.fulfill({ json: { id: account, version: previous.version } });
      }
      if (members.has(account) || body.expectedVersion !== null)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const candidate = accounts.get(account);
      if (!candidate?.active || candidate.invitationPending)
        return route.fulfill({ status: 400, json: { code: "account_unavailable" } });
      const selected: CompanyMemberGrantDto["roleTemplates"] = [];
      for (const input of body.roleTemplates) {
        const role = roles.records
          .get(company)
          ?.find((record) => record.current.id === input.id)?.current;
        if (!role?.active || role.version !== input.version || input.retainApplied)
          return route.fulfill({ status: 409, json: { code: "stale_role_template" } });
        selected.push({
          id: role.id,
          version: role.version,
          name: role.name,
          code: role.code,
          permissions: [...role.permissions],
        });
      }
      members.set(account, {
        member: {
          id: account,
          email: candidate.email,
          displayName: candidate.displayName,
          accountActive: true,
          active: true,
          permissions: [
            ...new Set([...body.permissions, ...selected.flatMap((role) => role.permissions)]),
          ].sort(),
          version: 0,
        },
        directPermissions: [...body.permissions],
        roleTemplates: selected,
      });
      receipts.set(`${company}:${operation}`, {
        id: account,
        version: 0,
        payload: JSON.stringify(body),
      });
      if (drop) {
        drop = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: account, version: 0 } });
    }
    reads.push(url);
    const held = heldRead;
    heldRead = null;
    if (held) {
      held.entered.release();
      await held.released.promise;
    }
    if (!account)
      return route.fulfill({
        json: {
          items: [...members.values()]
            .map((grant) => grant.member)
            .sort((a, b) => a.id.localeCompare(b.id)),
          nextCursor: null,
        },
      });
    const grant = members.get(account);
    return grant
      ? route.fulfill({ json: grant })
      : route.fulfill({ status: 404, json: { code: "company_member_not_found" } });
  });
  return {
    identity,
    roles,
    accounts,
    memberships,
    reads,
    writes,
    picker: `/administration/members/new?company=${companyIds[0]}`,
    editor: `/administration/members/new/${managedAccountId()}?company=${companyIds[0]}`,
    assignExisting: (company: string = companyIds[0]) => {
      const grant = companyMembershipGrant();
      grant.member = {
        ...grant.member,
        id: managedAccountId(),
        email: managedAccount().email,
        displayName: managedAccount().displayName,
      };
      memberships.get(company)?.set(managedAccountId(), grant);
    },
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      rejected = code;
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
  };
}
