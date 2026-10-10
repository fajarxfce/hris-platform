import type { Page } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

export const memberId = "21000000-0000-4000-8000-000000000001";
export async function installCompanyMembersApi(
  page: Page,
  options: { allowed?: boolean; longPage?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    permissions: options.allowed === false ? [] : ["identity.manage"],
  });
  const reads: URL[] = [];
  let failure: string | null = null;
  let hold: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/members**", async (route) => {
    const url = new URL(route.request().url());
    const match = /^\/api\/v1\/companies\/([^/]+)\/members(?:\/([^/]+))?$/u.exec(url.pathname);
    if (!match) return route.fallback();
    reads.push(url);
    const company = match[1];
    const paused = hold;
    hold = null;
    if (paused) {
      paused.entered();
      await paused.wait;
    }
    if (failure)
      return route.fulfill({
        status: 403,
        json: { code: failure, detail: "PRIVATE SERVER DETAIL" },
      });
    const members = Array.from(
      { length: options.longPage && company === companyIds[0] ? 51 : 1 },
      (_, index) => ({
        id: `21000000-0000-4000-8000-${(index + 1).toString().padStart(12, "0")}`,
        email:
          company === companyIds[0]
            ? `account${index + 1}@example.invalid`
            : "other-company@example.invalid",
        displayName: company === companyIds[0] ? `Nusa member ${index + 1}` : "Lintas member",
        accountActive: true,
        active: true,
        permissions: ["people.self.read"],
        version: 3,
      }),
    );
    if (match[2]) {
      const member = members.find((item) => item.id === match[2]);
      if (!member)
        return route.fulfill({ status: 404, json: { code: "company_member_not_found" } });
      return route.fulfill({
        json: {
          member,
          directPermissions: [],
          roleTemplates: [
            {
              id: "31000000-0000-4000-8000-000000000001",
              code: "EMPLOYEE",
              name: "Employee access",
              permissions: ["people.self.read"],
              version: 2,
            },
          ],
        },
      });
    }
    const after = url.searchParams.get("after");
    const filtered = members.filter((item) => after === null || item.id > after);
    const items = filtered.slice(0, 50);
    return route.fulfill({
      json: { items, nextCursor: filtered.length > items.length ? items.at(-1)?.id : null },
    });
  });
  return {
    identity,
    reads,
    fail: (code: string) => {
      failure = code;
    },
    holdNextRead: () => {
      let entered!: () => void;
      let release!: () => void;
      const start = new Promise<void>((resolve) => {
        entered = resolve;
      });
      const wait = new Promise<void>((resolve) => {
        release = resolve;
      });
      hold = { entered, wait };
      return { start, release };
    },
  };
}
