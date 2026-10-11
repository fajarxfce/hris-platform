import type { Page } from "@playwright/test";
import type {
  CalendarReviewDto,
  RosterDayChangeDto,
  ShiftSnapshotDto,
  WeeklyScheduleChangeDto,
} from "../src/features/workforce/data/models/calendar-dto";
import {
  calendarDtoFixture,
  employeeDtoFixture,
  employeeId,
  identifier,
  shiftDtoFixture,
} from "../src/features/workforce/di/calendar-fixture";
import { weekdays } from "../src/features/workforce/domain/entities/schedule-assignment";
import { companyIds, installIdentityApi } from "./identity-api";

export { employeeId, identifier };

function gate() {
  let release!: () => void;
  let enter!: () => void;
  return {
    held: new Promise<void>((done) => {
      release = done;
    }),
    started: new Promise<void>((done) => {
      enter = done;
    }),
    release: () => release(),
    enter: () => enter(),
  };
}

/** Bounded transport fixture; real calendar rules are exercised against PostgreSQL separately. */
export async function installCalendarsApi(
  page: Page,
  options: { permissions?: readonly string[] } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["workforce.read", "workforce.manage"],
  });
  const shifts = Array.from({ length: 55 }, (_, index) => shiftDtoFixture(index + 1));
  const original = shifts[0];
  if (!original) throw new Error("Missing fixture shift");
  let assignment: NonNullable<CalendarReviewDto["assignmentAtStart"]> = {
    effectiveFrom: "2026-01-01",
    revision: 2,
    days: { MONDAY: { id: original.id, revision: 0, details: original.details } },
  };
  let scheduleVersion = 4;
  const rosters = new Map<string, { shift: ShiftSnapshotDto | null; version: number }>([
    ["2026-10-13", { shift: null, version: 6 }],
  ]);
  const reads: URL[] = [];
  const writes: {
    company: string;
    path: string;
    operation: string | undefined;
    body: WeeklyScheduleChangeDto | RosterDayChangeDto;
  }[] = [];
  const receipts = new Map<string, { payload: string; version: number }>();
  let lost = false;
  let rejection: string | null = null;
  let pendingSave: ReturnType<typeof gate> | null = null;
  let pendingRead: ReturnType<typeof gate> | null = null;
  let commits = 0;

  await page.route("**/api/v1/companies/*/workforce/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    if (request.method() === "PUT") {
      const body = request.postDataJSON() as WeeklyScheduleChangeDto | RosterDayChangeDto;
      const operation = request.headers()["idempotency-key"];
      writes.push({ company, path: url.pathname, operation, body });
      const waiting = pendingSave;
      pendingSave = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      if (rejection) {
        const code = rejection;
        rejection = null;
        return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
      }
      const key = `${company}:${operation}`;
      const payload = JSON.stringify({ path: url.pathname, body });
      const receipt = receipts.get(key);
      if (receipt)
        return route.fulfill(
          receipt.payload === payload
            ? { json: { id: employeeId, version: receipt.version } }
            : { status: 409, json: { code: "operation_payload_mismatch" } },
        );
      const date = url.pathname.split("/").at(-1) ?? "";
      const weekly = "days" in body;
      const expected = weekly ? scheduleVersion : (rosters.get(date)?.version ?? null);
      if (body.expectedVersion !== expected)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      const references = weekly ? Object.values(body.days) : body.shift ? [body.shift] : [];
      if (
        references.some(
          (reference) =>
            !shifts.some(
              (shift) =>
                shift.id === reference.id && shift.version === reference.version && shift.active,
            ),
        )
      )
        return route.fulfill({ status: 409, json: { code: "shift_changed" } });
      const snapshot = (id: string): ShiftSnapshotDto => {
        const shift = shifts.find((item) => item.id === id);
        if (!shift) throw new Error("Unknown fixture shift");
        return { id, revision: shift.version, details: shift.details };
      };
      const version = expected === null ? 0 : expected + 1;
      if (weekly) {
        scheduleVersion = version;
        assignment = {
          effectiveFrom: body.effectiveFrom,
          revision: version,
          days: Object.fromEntries(
            Object.entries(body.days).map(([day, shift]) => [day, snapshot(shift.id)]),
          ),
        };
      } else rosters.set(date, { shift: body.shift ? snapshot(body.shift.id) : null, version });
      receipts.set(key, { payload, version });
      commits += 1;
      if (lost) {
        lost = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: employeeId, version } });
    }
    reads.push(url);
    if (url.pathname.endsWith("/shifts")) {
      const query = url.searchParams.get("query")?.toLowerCase() ?? "";
      const after = url.searchParams.get("after") ?? "";
      const items = shifts.filter(
        (shift) =>
          shift.details.code > after &&
          `${shift.details.name} ${shift.details.code}`.toLowerCase().includes(query),
      );
      return route.fulfill({
        json: {
          items: items.slice(0, 50),
          nextCursor: items.length > 50 ? items[49]?.details.code : null,
        },
      });
    }
    const employee = {
      ...employeeDtoFixture(),
      legalName: company === companyIds[0] ? "North Employee" : "South Employee",
    };
    if (url.pathname.endsWith("/employees"))
      return route.fulfill({ json: { items: [employee], nextCursor: null } });
    const from = url.searchParams.get("from") ?? "2026-10-12";
    const until = url.searchParams.get("until") ?? from;
    const fixture = calendarDtoFixture({ from, until });
    const review: CalendarReviewDto = {
      ...fixture,
      employee,
      assignmentAtStart: assignment,
      calendar: {
        scheduleVersion,
        days: fixture.calendar.days.map((day) => {
          const weekday = weekdays[(new Date(`${day.workDate}T00:00:00Z`).getUTCDay() + 6) % 7];
          const roster = rosters.get(day.workDate);
          const shift = roster ? roster.shift : weekday ? assignment.days[weekday] : null;
          return {
            ...day,
            kind: shift ? "WORK" : "OFF",
            shift: shift ?? null,
            startsAt: shift ? day.startsAt : null,
            endsAt: shift ? day.endsAt : null,
            plannedMinutes: shift ? 450 : 0,
            origin: roster ? "ROSTER" : "PATTERN",
            originVersion: roster?.version ?? assignment.revision,
          };
        }),
      },
    };
    const waiting = pendingRead;
    pendingRead = null;
    if (waiting) {
      waiting.enter();
      await waiting.held;
    }
    return route.fulfill({ json: review });
  });
  return {
    identity,
    reads,
    writes,
    get commits() {
      return commits;
    },
    loseNextSave: () => {
      lost = true;
    },
    rejectNextSave: (code: string) => {
      rejection = code;
    },
    replaceVersion: (version: number) => {
      scheduleVersion = version;
    },
    reviseShift: () => {
      shifts[0] = {
        ...original,
        version: 1,
        details: { ...original.details, name: "Revised shift" },
      };
    },
    holdSave: () => {
      const waiting = gate();
      pendingSave = waiting;
      return waiting;
    },
    holdReview: () => {
      const waiting = gate();
      pendingRead = waiting;
      return waiting;
    },
  };
}
