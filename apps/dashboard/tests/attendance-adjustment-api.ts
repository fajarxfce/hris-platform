import { expect, type Page } from "@playwright/test";
import type {
  AttendanceAdjustmentCommandDto,
  AttendanceAdjustmentDetailsDto,
} from "../src/features/workforce/data/models/attendance-adjustment-dto";
import {
  adjustmentDate,
  adjustmentDetailsDto,
  adjustmentReviewDto,
  employeeId,
  identifier,
  periodMonth,
} from "../src/features/workforce/di/attendance-adjustment-fixture";
import { workPeriodSnapshotDtoFixture } from "../src/features/workforce/di/work-period-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

/** Controlled transport failures; real database/worker acceptance runs separately. */
export async function installAttendanceAdjustmentApi(
  page: Page,
  options: { supported?: boolean; canCorrect?: boolean; canReview?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: ["company.read", "workforce.read", "attendance.correct", "attendance.verify"],
  });
  const company = companyIds[0],
    base = `/api/v1/companies/${company}/workforce/employees/${employeeId}/attendance/adjustments`;
  const original = workPeriodSnapshotDtoFixture();
  original.adjustmentsSupported = options.supported ?? true;
  let review = adjustmentReviewDto();
  review.canCorrect = options.canCorrect ?? true;
  review.canReviewEvidence = options.canReview ?? true;
  const reads: string[] = [],
    writes: { operation: string; body: AttendanceAdjustmentCommandDto }[] = [];
  const revisions = new Map<string, AttendanceAdjustmentDetailsDto>(),
    receipts = new Map<string, { body: string; id: string; version: number }>();
  let revision = 0,
    lose = false,
    rejected: string | null = null;
  let held: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route(
    `**/api/v1/companies/*/workforce/periods/${periodMonth}/employees/${employeeId}`,
    async (route) => route.fulfill({ json: original }),
  );
  await page.route(`**${base}**`, async (route) => {
    const request = route.request(),
      url = new URL(request.url());
    if (request.method() === "GET") {
      reads.push(url.pathname + url.search);
      let body: unknown;
      if (url.pathname.endsWith("/review")) body = structuredClone(review);
      else if (url.pathname === base) {
        expect(url.searchParams.get("limit")).toBe("25");
        const before = Number(url.searchParams.get("before") ?? Infinity);
        const all = [...revisions.values()]
          .map((item) => item.summary)
          .filter((item) => item.revision < before)
          .sort((a, b) => b.revision - a.revision);
        body = {
          items: all.slice(0, 25),
          nextCursor: all.length > 25 ? String(all[24]?.revision) : null,
        };
      } else body = revisions.get(url.pathname.split("/").at(-1) ?? "");
      if (held) {
        const pending = held;
        held = null;
        pending.entered();
        await pending.wait;
      }
      return body
        ? route.fulfill({ json: body })
        : route.fulfill({ status: 404, json: { code: "attendance_adjustment_not_found" } });
    }
    if (request.method() !== "POST" || url.pathname !== base) return route.fallback();
    const operation = request.headers()["idempotency-key"] ?? "",
      body = request.postDataJSON() as AttendanceAdjustmentCommandDto;
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-/u);
    writes.push({ operation, body });
    if (rejected) {
      const code = rejected;
      rejected = null;
      return route.fulfill({ status: 409, json: { code, detail: "PRIVATE INTERNAL DIAGNOSTIC" } });
    }
    const existing = receipts.get(operation);
    if (existing) {
      expect(existing.body).toBe(JSON.stringify(body));
      return route.fulfill({ json: { id: existing.id, version: existing.version } });
    }
    if (body.reviewRevision !== review.revision)
      return route.fulfill({ status: 409, json: { code: "attendance_adjustment_changed" } });
    revision++;
    const details = adjustmentDetailsDto(revision),
      day = details.snapshot.days[4];
    if (!day) throw new Error("Adjustment day required");
    day.fact = body.clockIn === null ? "ABSENCE_RECORDED" : "WORKED";
    day.acceptedMinutes =
      body.clockIn === null || body.clockOut === null
        ? 0
        : (Date.parse(body.clockOut) - Date.parse(body.clockIn)) / 60_000 - body.breakMinutes;
    day.evidenceIds = review.attendance.entries.map((entry) => entry.id);
    details.summary.reason = body.reason;
    revisions.set(details.summary.id, details);
    review = {
      ...review,
      current: structuredClone(day),
      revision: String(revision + 1).padStart(64, "0"),
      adjustmentRevision: revision,
      attendance: {
        ...review.attendance,
        acceptedMinutes: day.acceptedMinutes,
        pendingCount: 0,
        correction: {
          id: details.summary.correctionId,
          clockIn: body.clockIn,
          clockOut: body.clockOut,
          breakMinutes: body.breakMinutes,
          reason: body.reason,
          actorId: identifier(800),
          recordedAt: details.summary.recordedAt,
          version: revision - 1,
        },
        entries: review.attendance.entries.map((entry) => {
          const decision = body.decisions.find((item) => item.eventId === entry.id);
          return decision
            ? {
                ...entry,
                version: 1,
                status:
                  decision.decision === "ACCEPT" ? ("ACCEPTED" as const) : ("REJECTED" as const),
                review: {
                  decision: decision.decision,
                  actorId: identifier(800),
                  reviewedAt: details.summary.recordedAt,
                  reason: body.reason,
                },
              }
            : entry;
        }),
      },
    };
    const receipt = { id: details.summary.id, version: revision };
    receipts.set(operation, { ...receipt, body: JSON.stringify(body) });
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    identity,
    reads,
    writes,
    original,
    loseNext: () => {
      lose = true;
    },
    rejectNext: (code: string) => {
      rejected = code;
    },
    changeReview: () => {
      review = { ...review, revision: "f".repeat(64) };
    },
    populateHistory: (count: number) => {
      for (let index = 1; index <= count; index++) {
        const value = adjustmentDetailsDto(index);
        revisions.set(value.summary.id, value);
      }
    },
    holdRead: () => {
      let release!: () => void, entered!: () => void;
      const wait = new Promise<void>((done) => {
          release = done;
        }),
        started = new Promise<void>((done) => {
          entered = done;
        });
      held = { entered: () => entered(), wait };
      return { started, release: () => release() };
    },
  };
}
export const adjustmentResultPath = `/workforce/periods/${periodMonth}/employees/${employeeId}?company=${companyIds[0]}`;
export const adjustmentEditorPath = `/workforce/periods/${periodMonth}/employees/${employeeId}/days/${adjustmentDate}/adjustment?company=${companyIds[0]}`;
export const adjustmentHistoryPath = `/workforce/periods/${periodMonth}/employees/${employeeId}/adjustments?company=${companyIds[0]}`;
