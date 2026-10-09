import type { Page } from "@playwright/test";

export const companyIds = [
  "10000000-0000-4000-8000-000000000001",
  "10000000-0000-4000-8000-000000000002",
] as const;

/** Owned transport fixture: browser tests exercise production DI, mapping, controllers and views. */
export async function installIdentityApi(
  page: Page,
  options: {
    signedIn?: boolean;
    mfa?: boolean;
    failLogin?: string;
    noCompanies?: boolean;
    permissions?: readonly string[];
  } = {},
) {
  let signedIn = options.signedIn ?? false;
  let verified = !options.mfa;
  let configured = false;
  let csrf = 0;
  const commands: { path: string; body: unknown; csrf: string | undefined }[] = [];
  const unhandled: string[] = [];
  const recoveryCodes = ["AAAA-BBBB-CCCC", "DDDD-EEEE-FFFF"];
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const respond = (json: unknown, status = 200) => route.fulfill({ json, status });
    if (method === "GET" && path === "/api/v1/auth/csrf") {
      csrf += 1;
      return respond({ token: `fixture-csrf-${csrf}`, headerName: "X-CSRF-TOKEN" });
    }
    if (method === "GET" && path === "/api/v1/auth/providers") {
      return respond([
        { id: "company", name: "Company SSO", authorizationPath: "/oauth2/authorization/company" },
      ]);
    }
    if (method !== "GET") {
      commands.push({
        path,
        body: request.postDataJSON(),
        csrf: request.headers()["x-csrf-token"],
      });
      if (request.headers()["x-csrf-token"] !== `fixture-csrf-${csrf}`)
        return respond({ code: "csrf_invalid", fields: {}, parameters: {} }, 403);
    }
    if (path === "/api/v1/auth/login") {
      if (options.failLogin)
        return respond(
          {
            code: options.failLogin,
            fields: {},
            parameters: {},
            detail: "INTERNAL TECHNICAL TEXT MUST NOT BE DISPLAYED",
          },
          401,
        );
      signedIn = true;
      return respond({ authenticated: true });
    }
    if (path === "/api/v1/auth/logout") {
      signedIn = false;
      return respond({ authenticated: false });
    }
    if (path === "/api/v1/me") {
      if (!signedIn)
        return respond({ code: "authentication_required", fields: {}, parameters: {} }, 401);
      return respond({
        account: {
          id: "20000000-0000-4000-8000-000000000001",
          email: "reviewer@example.invalid",
          displayName: "Sample Reviewer",
          mfaConfigured: configured,
        },
        companies: options.noCompanies
          ? []
          : companyIds.map((id, index) => ({
              id,
              code: index === 0 ? "NORTH" : "SOUTH",
              name: index === 0 ? "North Company" : "South Company",
              timezone: "Asia/Jakarta",
            })),
        permissions: [],
        assurance: {
          required: Boolean(options.mfa),
          verified,
          setupAvailable: true,
          validUntil: null,
          recentUntil: null,
        },
      });
    }
    if (path.endsWith("/me/access")) {
      const companyId = path.split("/")[4];
      return respond({
        companyId,
        permissions: options.permissions ?? ["company.read", "people.read"],
      });
    }
    if (path === "/api/v1/auth/mfa/enrollment") {
      return respond({
        operationId: request.headers()["idempotency-key"],
        secret: "JBSWY3DPEHPK3PXP",
        otpauthUri: "otpauth://totp/HRIS:fixture?secret=JBSWY3DPEHPK3PXP",
        expiresAt: "2030-01-01T00:00:00Z",
      });
    }
    if (path === "/api/v1/auth/mfa/enrollment/confirm") {
      configured = true;
      verified = true;
      return respond({ verifiedAt: "2026-10-01T00:00:00Z", recoveryCodes });
    }
    unhandled.push(`${method} ${path}`);
    return respond({ code: "unexpected_fixture_request", fields: {}, parameters: {} }, 500);
  });
  return { commands, unhandled, recoveryCodes };
}
