import { expect, type Page } from "@playwright/test";
import { companyIds } from "./identity-api";
import {
  installPersonProfileApi,
  profileEmployeeIds,
  profilePersonIds,
} from "./person-profile-api";

export const bindingTarget = "20000000-0000-4000-8000-000000000051";
export async function installAccountBindingApi(
  page: Page,
  options: { emptyFirstPage?: boolean; allowed?: boolean } = {},
) {
  const profile = await installPersonProfileApi(page, {
    permissions:
      options.allowed === false
        ? ["people.profile.read"]
        : [
            "people.read",
            "people.profile.read",
            "people.profile.manage",
            "identity.manage",
            "people.account.link",
          ],
  });
  profile.replaceProfile({ accountId: null });
  const commands: { operation: string | undefined; body: unknown; csrf: string | undefined }[] = [];
  const receipts = new Map<string, { payload: string; version: number }>();
  let version = 7;
  let commits = 0;
  let lose = false;
  let rejection: string | null = null;
  await page.route("**/api/v1/companies/*/members?*", async (route) => {
    const url = new URL(route.request().url());
    expect(url.searchParams.get("limit")).toBe("50");
    const member = (id: string, displayName: string, active: boolean) => ({
      id,
      email: "account@example.invalid",
      displayName,
      accountActive: true,
      active,
      permissions: ["people.self.read"],
      version: 1,
    });
    const firstPage = !url.searchParams.has("after");
    if (options.emptyFirstPage && firstPage) {
      const items = Array.from({ length: 50 }, (_, index) =>
        member(
          `20000000-0000-4000-8000-${(index + 1).toString().padStart(12, "0")}`,
          index === 0 ? "Reviewer account" : `Inactive ${index}`,
          index === 0,
        ),
      );
      return route.fulfill({ json: { items, nextCursor: items.at(-1)?.id } });
    }
    return route.fulfill({
      json: { items: [member(bindingTarget, "Employee account", true)], nextCursor: null },
    });
  });
  await page.route(
    `**/api/v1/companies/${companyIds[0]}/employees/${profileEmployeeIds[0]}/account-link`,
    async (route) => {
      const request = route.request();
      expect(request.method()).toBe("POST");
      const body = request.postDataJSON() as {
        accountId: string;
        expectedVersion: number;
        reason: string;
      };
      const operation = request.headers()["idempotency-key"];
      const csrf = request.headers()["x-csrf-token"];
      commands.push({ body, operation, csrf });
      expect(csrf).toMatch(/^fixture-csrf-/u);
      if (rejection) {
        const code = rejection;
        rejection = null;
        return route.fulfill({ status: 409, json: { code } });
      }
      const fingerprint = JSON.stringify(body);
      let receipt = receipts.get(operation ?? "");
      if (receipt && receipt.payload !== fingerprint)
        return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
      if (!receipt) {
        if (body.expectedVersion !== version)
          return route.fulfill({ status: 409, json: { code: "stale_version" } });
        expect(body.accountId).toBe(bindingTarget);
        version++;
        profile.replaceProfile({ accountId: bindingTarget, version });
        receipt = { payload: fingerprint, version };
        receipts.set(operation ?? "", receipt);
        commits++;
      }
      if (lose) {
        lose = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: profilePersonIds[0], version: receipt.version } });
    },
  );
  return {
    profile,
    commands,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectNext: (code: string) => {
      rejection = code;
    },
  };
}
