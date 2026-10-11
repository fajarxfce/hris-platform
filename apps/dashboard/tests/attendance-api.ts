import type { Page } from "@playwright/test";
import type {
  AttendanceCorrectionChangeDto,
  AttendanceDayDto,
  AttendanceReviewDto,
} from "../src/features/workforce/data/models/attendance-dto";
import { attendanceDtoFixture, eventId } from "../src/features/workforce/di/attendance-fixture";
import { employeeId, identifier, installCalendarsApi } from "./calendars-api";

export { employeeId, eventId, identifier };

const date = "2026-10-12";
export async function installAttendanceApi(
  page: Page,
  options: { permissions?: readonly string[] } = {},
) {
  const calendars = await installCalendarsApi(page, {
    permissions: options.permissions ?? [
      "workforce.read",
      "attendance.verify",
      "attendance.correct",
    ],
  });
  const records = new Map<string, AttendanceDayDto>();
  const writes: {
    path: string;
    operation: string | undefined;
    body: AttendanceReviewDto | AttendanceCorrectionChangeDto;
  }[] = [];
  const receipts = new Map<string, { body: string; id: string; version: number }>();
  let commits = 0,
    loseNext = false;
  let rejection: string | null = null;
  function day(value: string): AttendanceDayDto {
    let found = records.get(value);
    if (!found) {
      found = attendanceDtoFixture({ from: value, until: value })[0];
      if (!found) throw new Error("Missing day");
      for (const entry of found.entries)
        entry.id =
          value === date
            ? eventId
            : identifier(
                100000 +
                  Math.floor(
                    (Date.parse(`${value}T00:00:00Z`) - Date.parse("0001-01-01T00:00:00Z")) /
                      86400000,
                  ),
              );
      records.set(value, found);
    }
    return found;
  }
  await page.route("**/api/v1/companies/*/workforce/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url());
    if (!url.pathname.includes("/attendance")) return route.fallback();
    if (request.method() === "GET") {
      const from = url.searchParams.get("from") ?? date,
        until = url.searchParams.get("until") ?? from;
      const start = Date.parse(`${from}T00:00:00Z`),
        count = (Date.parse(`${until}T00:00:00Z`) - start) / 86_400_000 + 1;
      return route.fulfill({
        json: Array.from({ length: count }, (_, index) =>
          day(new Date(start + index * 86_400_000).toISOString().slice(0, 10)),
        ),
      });
    }
    const body = request.postDataJSON() as AttendanceReviewDto | AttendanceCorrectionChangeDto;
    const operation = request.headers()["idempotency-key"];
    writes.push({ path: url.pathname, operation, body });
    if (rejection) {
      const code = rejection;
      rejection = null;
      return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
    }
    const receipt = receipts.get(operation ?? "");
    if (receipt) {
      if (receipt.body !== JSON.stringify(body)) throw new Error("Changed idempotent request");
      return route.fulfill({ json: { id: receipt.id, version: receipt.version, replayed: true } });
    }
    let id: string, version: number;
    if ("decision" in body) {
      const record = day(date),
        entry = record.entries[0];
      if (!entry || entry.version !== body.version || entry.status !== "PENDING")
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      id = entry.id;
      version = entry.version + 1;
      entry.status = body.decision === "ACCEPT" ? "ACCEPTED" : "REJECTED";
      entry.version = version;
      entry.review = {
        actorId: identifier(1),
        decision: body.decision,
        reviewedAt: "2026-10-13T03:00:00Z",
        reason: body.reason,
      };
      record.pendingCount = 0;
      record.incomplete = entry.status === "ACCEPTED";
    } else {
      const record = day(body.workDate);
      if ((record.correction?.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      id = identifier(920 + commits);
      version = body.expectedVersion === null ? 0 : body.expectedVersion + 1;
      record.correction = {
        ...body,
        id,
        actorId: identifier(1),
        recordedAt: "2026-10-13T03:00:00Z",
        version,
      };
      record.acceptedMinutes =
        body.clockIn && body.clockOut
          ? (Date.parse(body.clockOut) - Date.parse(body.clockIn)) / 60_000 - body.breakMinutes
          : 0;
      record.incomplete = false;
    }
    commits++;
    receipts.set(operation ?? "", { body: JSON.stringify(body), id, version });
    if (loseNext) {
      loseNext = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version } });
  });
  return {
    identity: calendars.identity,
    writes,
    get commits() {
      return commits;
    },
    loseNextSave: () => {
      loseNext = true;
    },
    rejectNextSave: (code: string) => {
      rejection = code;
    },
    resolvePending: () => {
      const record = day(date);
      record.entries = [];
      record.pendingCount = 0;
    },
    replaceCorrection: () => {
      const record = day(date);
      record.correction = {
        id: identifier(900),
        clockIn: null,
        clockOut: null,
        breakMinutes: 0,
        reason: "Competing absence",
        actorId: identifier(1),
        recordedAt: "2026-10-13T02:00:00Z",
        version: 0,
      };
    },
  };
}
