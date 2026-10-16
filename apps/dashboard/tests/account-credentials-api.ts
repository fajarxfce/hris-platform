import { expect, type Page } from "@playwright/test";
import { installIdentityApi } from "./identity-api";
export const ownedCredentialToken = "c".repeat(43);
export async function installAccountCredentialsApi(page: Page, signedIn = false) {
  const identity = await installIdentityApi(page, { signedIn });
  const confirmations: { path: string; token: string; password: string }[] = [];
  const recoveries: string[] = [];
  const consumed = new Set<string>();
  let drop = false;
  await page.route("**/api/v1/auth/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (
      ![
        "/api/v1/auth/password-recovery",
        "/api/v1/auth/invitations/accept",
        "/api/v1/auth/password-recovery/confirm",
      ].includes(path)
    )
      return route.fallback();
    expect(route.request().headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    expect(route.request().headers().authorization).toBeUndefined();
    const body = route.request().postDataJSON();
    if (path === "/api/v1/auth/password-recovery") {
      recoveries.push(body.email);
      return route.fulfill({ status: 202, body: "" });
    }
    confirmations.push({ path, token: body.token, password: body.password });
    if (body.token !== ownedCredentialToken || consumed.has(body.token))
      return route.fulfill({
        status: 422,
        json: { code: "credential_link_unavailable", detail: "OWNED PRIVATE DIAGNOSTIC" },
      });
    consumed.add(body.token);
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ status: 204 });
  });
  return {
    identity,
    confirmations,
    recoveries,
    consumed,
    dropConfirmation: () => {
      drop = true;
    },
  };
}
