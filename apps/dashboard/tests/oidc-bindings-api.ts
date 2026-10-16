import { expect, type Page } from "@playwright/test";
import type { OidcBindingChangeDto } from "../src/features/identity/data/models/oidc-binding-change-dto";
import type { OidcBindingDto } from "../src/features/identity/data/models/oidc-binding-dto";
import { managedAccountId, platformActorId } from "./fixtures/managed-accounts";
import { oidcBinding } from "./fixtures/oidc-bindings";
import { installManagedAccountsApi } from "./managed-accounts-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}
export async function installOidcBindingsApi(page: Page, longPage = false) {
  const f = await installManagedAccountsApi(page, { noCompanies: true });
  const bindings = new Map<string, OidcBindingDto & { accountId: string }>(
    Array.from({ length: longPage ? 51 : 1 }, (_, i) => {
      const item = { ...oidcBinding(i + 1), active: i === 0, accountId: managedAccountId() };
      return [item.id, item];
    }),
  );
  const reads: URL[] = [];
  const writes: { accountId: string; id: string; operation: string; body: OidcBindingChangeDto }[] =
    [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let rejected: string | null = null;
  let dropped = false;
  let heldSave: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  let heldRead: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null =
    null;
  await page.route("**/api/v1/identity/accounts/*/oidc**", async (route) => {
    const url = new URL(route.request().url());
    const match = /^\/api\/v1\/identity\/accounts\/([^/]+)\/oidc(?:\/([^/]+))?$/u.exec(
      url.pathname,
    );
    if (!match?.[1]) return route.fallback();
    const accountId = match[1];
    const id = match[2];
    if (route.request().method() === "PUT" && id) {
      const operation = route.request().headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      const body = route.request().postDataJSON() as OidcBindingChangeDto;
      writes.push({ accountId, id, operation, body });
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
        expect(previous.payload).toBe(JSON.stringify({ accountId, id, body }));
        return route.fulfill({ json: { id, version: previous.version } });
      }
      const account = f.records.get(accountId);
      if (!account) return route.fulfill({ status: 404, json: { code: "account_not_found" } });
      if (!account.active && body.active)
        return route.fulfill({ status: 409, json: { code: "account_disabled" } });
      const binding = bindings.get(id);
      if ((binding?.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      if (
        binding &&
        (binding.accountId !== accountId ||
          binding.issuer !== body.issuer ||
          binding.subject !== body.subject)
      )
        return route.fulfill({ status: 409, json: { code: "oidc_binding_immutable" } });
      if (
        body.active &&
        [...bindings.values()].some(
          (other) =>
            other.id !== id &&
            other.active &&
            other.accountId === accountId &&
            other.issuer === body.issuer,
        )
      )
        return route.fulfill({ status: 409, json: { code: "data_conflict" } });
      const version = (body.expectedVersion ?? -1) + 1;
      bindings.set(id, {
        id,
        accountId,
        issuer: body.issuer,
        subject: body.subject,
        active: body.active,
        version,
        linkedAt: binding?.linkedAt ?? "2026-10-16T09:00:00Z",
      });
      f.records.set(accountId, { ...account, version: account.version + 1 });
      receipts.set(operation, { id, version, payload: JSON.stringify({ accountId, id, body }) });
      if (accountId === platformActorId) f.identity.revoke();
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
    if (id) {
      const binding = bindings.get(id);
      return binding?.accountId === accountId
        ? route.fulfill({ json: binding })
        : route.fulfill({ status: 404, json: { code: "oidc_identity_not_found" } });
    }
    expect(url.searchParams.get("size")).toBe("50");
    const after = url.searchParams.get("after") ?? "";
    const available = [...bindings.values()]
      .filter((item) => item.accountId === accountId && item.id > after)
      .sort((a, b) => a.id.localeCompare(b.id));
    const items = available.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: available.length > 50 ? items.at(-1)?.id : null },
    });
  });
  return {
    ...f,
    bindings,
    oidcReads: reads,
    oidcWrites: writes,
    oidcReceipts: receipts,
    rejectBinding: (code: string) => {
      rejected = code;
    },
    dropBinding: () => {
      dropped = true;
    },
    holdBinding: () => {
      if (heldSave) throw new Error("A binding save is already held");
      heldSave = { entered: gate(), released: gate() };
      return { entered: heldSave.entered.promise, release: heldSave.released.release };
    },
    holdBindingRead: () => {
      if (heldRead) throw new Error("A binding read is already held");
      heldRead = { entered: gate(), released: gate() };
      return { entered: heldRead.entered.promise, release: heldRead.released.release };
    },
  };
}
