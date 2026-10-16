import { expect, type Page } from "@playwright/test";
import type { AccountInvitationDto } from "../src/features/identity/data/models/account-invitation-dto";
import { installManagedAccountsApi } from "./managed-accounts-api";

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((done) => {
    release = done;
  });
  return { promise, release };
}
export async function installAccountInvitationsApi(page: Page) {
  const accounts = await installManagedAccountsApi(page, { noCompanies: true });
  const writes: { operation: string; body: AccountInvitationDto }[] = [];
  const receipts = new Map<string, { id: string; version: number; body: string }>();
  let rejection: string | null = null;
  let drop = false;
  let held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  await page.route("**/api/v1/identity/invitations", async (route) => {
    const body = route.request().postDataJSON() as AccountInvitationDto;
    const operation = route.request().headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    writes.push({ operation, body });
    const waiting = held;
    held = null;
    if (waiting) {
      waiting.entered.release();
      await waiting.released.promise;
    }
    if (rejection) {
      const code = rejection;
      rejection = null;
      return route.fulfill({
        status: code === "mfa_required" ? 403 : code === "mail_not_configured" ? 503 : 409,
        json: { code },
      });
    }
    const receipt = receipts.get(operation);
    if (receipt) {
      expect(receipt.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: { id: receipt.id, version: receipt.version } });
    }
    const account = accounts.records.get(body.id);
    if (account && (!account.invitationPending || account.active || account.email !== body.email))
      return route.fulfill({ status: 409, json: { code: "account_cannot_be_invited" } });
    if ((account?.version ?? null) !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const version = (body.expectedVersion ?? -1) + 1;
    accounts.records.set(body.id, {
      id: body.id,
      email: body.email,
      displayName: body.displayName,
      active: false,
      invitationPending: true,
      mfaConfigured: false,
      platformPermissions: [],
      version,
    });
    receipts.set(operation, { id: body.id, version, body: JSON.stringify(body) });
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id: body.id, version } });
  });
  return {
    ...accounts,
    invitationWrites: writes,
    invitationReceipts: receipts,
    rejectInvitation: (code: string) => {
      rejection = code;
    },
    dropInvitation: () => {
      drop = true;
    },
    holdInvitation: () => {
      if (held) throw new Error("Already held");
      held = { entered: gate(), released: gate() };
      return { entered: held.entered.promise, release: held.released.release };
    },
  };
}
