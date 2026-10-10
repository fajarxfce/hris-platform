import { expect, type Page } from "@playwright/test";
import { leaveEvidence } from "./fixtures/leave-evidence";
import { installLeaveActionsApi } from "./leave-actions-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installLeaveEvidenceApi(page: Page) {
  const base = await installLeaveActionsApi(page);
  const evidence = leaveEvidence();
  base.record.attachments = [evidence.attachment];
  const downloads: string[] = [];
  let failure: string | null = null;
  let invalid: "etag" | "body" | null = null;
  let held: { entered: ReturnType<typeof gate>; release: ReturnType<typeof gate> } | null = null;
  await page.route(
    "**/api/v1/companies/*/leave/requests/*/attachments/*/content",
    async (route) => {
      const request = route.request();
      downloads.push(request.url());
      expect(request.method()).toBe("GET");
      expect(request.headers().accept).toContain("application/pdf");
      expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
      const wait = held;
      held = null;
      const error = failure;
      failure = null;
      const malformed = invalid;
      invalid = null;
      if (wait) {
        wait.entered.resolve();
        await wait.release.promise;
      }
      if (error)
        return route.fulfill({
          status: error === "mfa_required" ? 403 : 404,
          json: { code: error, fields: {}, parameters: {}, detail: "PRIVATE STORAGE DETAILS" },
        });
      return route.fulfill({
        status: 200,
        headers: {
          "Content-Type": evidence.attachment.mediaType,
          "Content-Length": String(evidence.bytes.byteLength),
          ETag: malformed === "etag" ? '"obsolete-revision"' : evidence.etag,
          "Cache-Control": "private, no-store",
        },
        body: malformed === "body" ? evidence.bytes.subarray(0, 1) : evidence.bytes,
      });
    },
  );
  return {
    ...base,
    evidence,
    downloads,
    get commits() {
      return base.commits;
    },
    failDownload: (code: string) => {
      failure = code;
    },
    malformedDownload: (kind: "etag" | "body") => {
      invalid = kind;
    },
    holdDownload: () => {
      const entered = gate();
      const release = gate();
      held = { entered, release };
      return { entered: entered.promise, release: release.resolve };
    },
  };
}
