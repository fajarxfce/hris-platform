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
    mfaConfigured?: boolean;
    mfaVerified?: boolean;
    failLogin?: string;
    noCompanies?: boolean;
    permissions?: readonly string[];
    timezone?: string;
  } = {},
) {
  let signedIn = options.signedIn ?? false;
  let required = options.mfa ?? false;
  let verified = options.mfaVerified ?? !required;
  let configured = options.mfaConfigured ?? false;
  let permissions = options.permissions ?? ["company.read", "people.read"];
  let accessFailure: string | null = null;
  const reads = { session: 0, access: 0 };
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
      reads.session += 1;
      if (!signedIn)
        return respond({ code: "authentication_required", fields: {}, parameters: {} }, 401);
      return respond({
        account: {
          id: "20000000-0000-4000-8000-000000000001",
          email: "reviewer@example.invalid",
          displayName: "Sample Reviewer",
          mfaConfigured: configured,
        },
        companies:
          options.noCompanies || (required && !verified)
            ? []
            : companyIds.map((id, index) => ({
                id,
                code: index === 0 ? "NORTH" : "SOUTH",
                name: index === 0 ? "North Company" : "South Company",
                timezone: options.timezone ?? "Asia/Jakarta",
              })),
        permissions: [],
        assurance: {
          required,
          verified,
          setupAvailable: true,
          validUntil: null,
          recentUntil: null,
        },
      });
    }
    if (path.endsWith("/me/access")) {
      reads.access += 1;
      if (required && !verified) return respond({ code: "mfa_required" }, 403);
      if (accessFailure) return respond({ code: accessFailure }, 403);
      const companyId = path.split("/")[4];
      return respond({
        companyId,
        permissions,
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
    if (path === "/api/v1/auth/mfa/verify") {
      if (!signedIn) return respond({ code: "session_revoked" }, 401);
      if (request.postDataJSON().code === "000000")
        return respond({ code: "invalid_mfa_code" }, 401);
      configured = true;
      verified = true;
      return respond({ verifiedAt: "2026-10-01T00:00:00Z", recoveryCodes: [] });
    }
    unhandled.push(`${method} ${path}`);
    return respond({ code: "unexpected_fixture_request", fields: {}, parameters: {} }, 500);
  });
  return {
    commands,
    unhandled,
    recoveryCodes,
    reads,
    expireMfa: () => {
      required = true;
      verified = false;
    },
    renewMfa: () => {
      required = true;
      configured = true;
      verified = true;
    },
    revoke: () => {
      signedIn = false;
    },
    setPermissions: (next: readonly string[]) => {
      permissions = next;
    },
    denyAccess: (code: string | null) => {
      accessFailure = code;
    },
  };
}
