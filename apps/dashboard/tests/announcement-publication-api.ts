import { expect, type Page } from "@playwright/test";
import type { AnnouncementCommandDto } from "../src/features/communications/data/models/announcement-command-dto";
import type { AnnouncementDto } from "../src/features/communications/data/models/announcement-dto";
import type { AnnouncementReviewDto } from "../src/features/communications/data/models/announcement-review-dto";
import { publicationJobId } from "../src/features/communications/di/announcement-publication-fixture";
import { announcementId, detail } from "../src/features/communications/di/communications-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export async function installAnnouncementPublicationApi(page: Page, allowed = true) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    permissions: allowed ? ["announcements.manage"] : [],
  });
  let record: AnnouncementDto = detail(0);
  let job: AnnouncementReviewDto["publicationJob"] = null;
  let drop = false;
  let rejection: string | null = null;
  let commits = 0;
  let hold: { entered: () => void; wait: Promise<void> } | null = null;
  const reads: URL[] = [];
  const previews: URL[] = [];
  const writes: { operation: string | undefined; path: string; body: AnnouncementCommandDto }[] =
    [];
  const receipts = new Map<string, { payload: string; id: string; version: number }>();
  await page.route("**/api/v1/companies/*/announcements**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const match =
      /^\/api\/v1\/companies\/([^/]+)\/announcements(?:\/([^/]+))?(?:\/([^/]+))?$/u.exec(
        url.pathname,
      );
    if (!match) return route.fallback();
    const action = match[3];
    if (request.method() === "GET") {
      reads.push(url);
      if (match[1] !== companyIds[0])
        return route.fulfill({
          status: match[2] ? 404 : 200,
          json: match[2] ? { code: "announcement_not_found" } : { items: [], nextCursor: null },
        });
      if (action === "publication-review") {
        const stopped = job?.status === "FAILED" || job?.status === "CANCELLED";
        const availableActions: AnnouncementReviewDto["availableActions"] =
          record.status === "DRAFT"
            ? ["EDIT", "PREVIEW", "PUBLISH", "ARCHIVE"]
            : record.status === "QUEUED"
              ? stopped
                ? ["PREVIEW", "RETURN_TO_DRAFT", "ARCHIVE"]
                : ["PREVIEW"]
              : record.status === "PUBLISHED"
                ? ["ARCHIVE"]
                : [];
        return route.fulfill({
          json: {
            announcement: record,
            publicationJob: job,
            availableActions,
            evaluatedAt: "2026-10-11T01:00:00Z",
          },
        });
      }
      if (action === "audience-preview") {
        previews.push(url);
        const pending = hold;
        if (pending) {
          pending.entered();
          await pending.wait;
        }
        if (Number(url.searchParams.get("expectedVersion")) !== record.version)
          return route.fulfill({ status: 409, json: { code: "stale_version" } });
        return route.fulfill({
          json: {
            announcementId,
            version: record.version,
            asOfDate: "2026-10-11",
            evaluatedAt: "2026-10-11T01:00:00Z",
            recipientCount: 3,
            audienceVersions: {},
          },
        });
      }
      return route.fulfill({
        json: match[2]
          ? record
          : {
              items: [{ ...record, audienceKind: record.audience.kind, targetCount: 0 }],
              nextCursor: null,
            },
      });
    }
    expect(request.method()).toBe("POST");
    expect(["publish", "archive", "return-to-draft"]).toContain(action);
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    const operation = request.headers()["idempotency-key"];
    const body = request.postDataJSON() as AnnouncementCommandDto;
    writes.push({ operation, path: url.pathname, body });
    if (rejection) {
      const code = rejection;
      rejection = null;
      return route.fulfill({ status: code === "mfa_required" ? 403 : 409, json: { code } });
    }
    const payload = JSON.stringify([url.pathname, body]);
    let receipt = receipts.get(operation ?? "");
    if (receipt && receipt.payload !== payload)
      return route.fulfill({ status: 409, json: { code: "operation_payload_mismatch" } });
    if (!receipt) {
      if (body.expectedVersion !== record.version)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      record = { ...record, version: record.version + 1, reason: body.reason };
      if (action === "publish") {
        record = {
          ...record,
          status: "QUEUED",
          publicationJobId,
          publicationAttempts: record.publicationAttempts + 1,
          scheduledFor: body.scheduledFor ?? null,
        };
        job = {
          id: publicationJobId,
          status: "QUEUED",
          cancellationRequested: false,
          version: 0,
          failureCode: null,
        };
      } else if (action === "return-to-draft") {
        record = { ...record, status: "DRAFT", publicationJobId: null, scheduledFor: null };
        job = null;
      } else record = { ...record, status: "ARCHIVED" };
      receipt = { id: record.id, version: record.version, payload };
      receipts.set(operation ?? "", receipt);
      commits++;
    }
    if (drop) {
      drop = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id: receipt.id, version: receipt.version } });
  });
  return {
    identity,
    reads,
    previews,
    writes,
    get commits() {
      return commits;
    },
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      rejection = code;
    },
    revise: () => {
      record = { ...record, version: record.version + 1, title: "Reviewed office notice" };
    },
    stopPublication: () => {
      record = {
        ...record,
        status: "QUEUED",
        version: 1,
        publicationJobId,
        publicationAttempts: 1,
      };
      job = {
        id: publicationJobId,
        status: "FAILED",
        version: 2,
        cancellationRequested: false,
        failureCode: "announcement_audience_empty",
      };
    },
    holdPreviews: () => {
      let entered!: () => void;
      let release!: () => void;
      const start = new Promise<void>((resolve) => {
        entered = resolve;
      });
      const wait = new Promise<void>((resolve) => {
        release = resolve;
      });
      hold = { entered, wait };
      return {
        start,
        release: () => {
          hold = null;
          release();
        },
      };
    },
  };
}
