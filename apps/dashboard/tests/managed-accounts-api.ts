import { expect, type Page } from "@playwright/test";
import type { AccountAccessChangeDto } from "../src/features/identity/data/models/account-access-change-dto";
import type { ManagedAccountDto } from "../src/features/identity/data/models/managed-account-dto";
import { managedAccount, platformActorId, platformPermissions } from "./fixtures/managed-accounts";
import { installIdentityApi } from "./identity-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}

export async function installManagedAccountsApi(
  page: Page,
  options: { allowed?: boolean; longPage?: boolean; noCompanies?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: ["identity.manage"],
    platformPermissions: options.allowed === false ? [] : platformPermissions,
    noCompanies: options.noCompanies ?? false,
  });
  const records = new Map<string, ManagedAccountDto>(
    Array.from({ length: options.longPage ? 51 : 1 }, (_, i) => {
      const account = managedAccount(i + 1);
      return [account.id, account] as const;
    }),
  );
  const reads: URL[] = [];
  const writes: { id: string; operation: string; body: AccountAccessChangeDto }[] = [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let rejected: string | null = null;
  let readFailure: string | null = null;
  let dropped = false;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldSave: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  await page.route("**/api/v1/identity/accounts**", async (route) => {
    const url = new URL(route.request().url());
    const match = /^\/api\/v1\/identity\/accounts(?:\/([^/]+)(\/access)?)?$/u.exec(url.pathname);
    if (!match) return route.fallback();
    const id = match[1];
    if (route.request().method() === "PUT" && id && match[2]) {
      const operation = route.request().headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      const body = route.request().postDataJSON() as AccountAccessChangeDto;
      writes.push({ id, operation, body });
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
      const previous = receipts.get(operation);
      if (previous) {
        expect(previous.id).toBe(id);
        expect(JSON.stringify(body)).toBe(previous.payload);
        return route.fulfill({ json: { id, version: previous.version } });
      }
      const account = records.get(id);
      if (!account) return route.fulfill({ status: 404, json: { code: "account_not_found" } });
      if (account.version !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      if (body.active && account.invitationPending)
        return route.fulfill({ status: 409, json: { code: "invitation_acceptance_required" } });
      const version = account.version + 1;
      records.set(id, {
        ...account,
        active: body.active,
        invitationPending: false,
        platformPermissions: [...body.platformPermissions],
        version,
      });
      receipts.set(operation, { id, version, payload: JSON.stringify(body) });
      if (id === platformActorId) identity.revoke();
      if (dropped) {
        dropped = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id, version } });
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
        status: readFailure === "platform_administrator_required" ? 403 : 503,
        json: { code: readFailure },
      });
    if (id) {
      const account = records.get(id);
      return account
        ? route.fulfill({ json: { account, assignablePermissions: platformPermissions } })
        : route.fulfill({ status: 404, json: { code: "account_not_found" } });
    }
    expect(url.searchParams.get("limit")).toBe("50");
    const query = url.searchParams.get("query") ?? "";
    const after = url.searchParams.get("after") ?? "";
    const available = [...records.values()]
      .filter(
        (account) =>
          account.id > after &&
          (account.displayName.toLowerCase().includes(query) || account.email.includes(query)),
      )
      .sort((a, b) => a.id.localeCompare(b.id));
    const items = available.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: available.length > 50 ? items.at(-1)?.id : null },
    });
  });
  return {
    identity,
    records,
    reads,
    writes,
    receipts,
    failReads: (code: string | null) => {
      readFailure = code;
    },
    rejectNextSave: (code: string) => {
      rejected = code;
    },
    dropNextSave: () => {
      dropped = true;
    },
    holdRead: () => {
      if (heldRead) throw new Error("A read is already held");
      heldRead = { entered: gate(), released: gate() };
      return { entered: heldRead.entered.promise, release: heldRead.released.release };
    },
    holdSave: () => {
      if (heldSave) throw new Error("A save is already held");
      heldSave = { entered: gate(), released: gate() };
      return { entered: heldSave.entered.promise, release: heldSave.released.release };
    },
  };
}
