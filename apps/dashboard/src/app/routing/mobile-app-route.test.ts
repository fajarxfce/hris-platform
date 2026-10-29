import { describe, expect, it } from "vitest";
import { mobileAppRoute } from "./mobile-app-route";

describe("mobile app route", () => {
  it("hands only known routes to the exact environment scheme", () => {
    for (const environment of ["dev", "staging", "prod"]) {
      for (const path of ["overview", "account", "inbox", "data-sync", "pending-actions"]) {
        const scheme = environment === "prod" ? "hrismobile" : `hrismobile-${environment}`;
        expect(mobileAppRoute(`/app/${environment}/${path}`, "", "")).toEqual({
          href: `${scheme}://app/${path}`,
        });
      }
    }
  });
  it("preserves canonical resource identifiers without deriving authorization", () => {
    const path =
      "accounts/11111111-1111-4111-8111-111111111111/companies/22222222-2222-4222-8222-222222222222/inbox/33333333-3333-4333-8333-333333333333";
    expect(mobileAppRoute(`/app/dev/${path}`, "", "")).toEqual({
      href: `hrismobile-dev://app/${path}`,
    });
  });
  it("does not capture ordinary web or credential routes", () => {
    for (const path of ["/", "/auth/accept-invitation", "/companies/one", "/application"])
      expect(mobileAppRoute(path, "", "")).toBeNull();
  });
  it("rejects query and fragment payloads instead of passing them to the app", () => {
    expect(mobileAppRoute("/app/dev/inbox", "?read=true", "")).toEqual({ href: null });
    expect(mobileAppRoute("/app/dev/inbox", "", "#token=private")).toEqual({ href: null });
  });
  it("rejects malformed routes, escapes, control characters and oversized values", () => {
    for (const path of [
      "/app",
      "/app/",
      "/app/qa/inbox",
      "/app/DEV/inbox",
      "/app/dev/activity",
      "/app/dev/accounts/invalid/companies/invalid/inbox/invalid",
      "/app/dev/%69nbox",
      "/app/dev/inbox/",
      "/app/dev/inbox\n",
      "/app/dev/inbox\0",
      "/app/dev/../inbox",
      "/app/dev//inbox",
      "/app/dev/https://evil.example/",
      `/app/dev/${"a".repeat(4096)}`,
    ])
      expect(mobileAppRoute(path, "", "")).toEqual({ href: null });
  });
});
