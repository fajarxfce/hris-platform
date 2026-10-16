import type { Page } from "@playwright/test";
import type { RosterBulkApplyDto } from "../src/features/workforce/data/models/roster-bulk-dto";
import {
  employeeDtoFixture,
  identifier,
  shiftDtoFixture,
} from "../src/features/workforce/di/calendar-fixture";
import { bulkDtoFixture } from "../src/features/workforce/di/roster-bulk-fixture";
import type { RosterBulkSelection } from "../src/features/workforce/domain/entities/roster-bulk-selection";
import { companyIds, installIdentityApi } from "./identity-api";

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

export async function installRosterBulkApi(
  page: Page,
  permissions = ["workforce.read", "workforce.manage"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    permissions,
  });
  const employee = (company: string, index: number) => ({
    ...employeeDtoFixture(),
    id: identifier(index + 500),
    legalName: `${company === companyIds[0] ? "North" : "South"} Employee ${index}`,
    employeeNumber: `EMP-${String(index).padStart(3, "0")}`,
  });
  const shifts = Array.from({ length: 55 }, (_, index) => shiftDtoFixture(index + 1));
  const reads: URL[] = [];
  const reviews: { company: string; selection: RosterBulkSelection }[] = [];
  const writes: { company: string; operation: string; body: RosterBulkApplyDto }[] = [];
  const receipts = new Map<string, string>();
  let revision = 1,
    commits = 0,
    unchanged = false,
    lost = false;
  let supported = true;
  let rejected: string | null = null;
  let pendingReview: ReturnType<typeof gate> | null = null;
  let pendingSave: ReturnType<typeof gate> | null = null;
  let pendingShifts: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/workforce/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4] ?? "";
    if (request.method() === "POST" && url.pathname.endsWith("/bulk/review")) {
      const selection = request.postDataJSON() as RosterBulkSelection;
      reviews.push({ company, selection });
      const dto = bulkDtoFixture(selection);
      const selectedShift = shifts.find((shift) => shift.id === selection.shift?.id);
      if (selectedShift)
        for (const entry of dto.employees)
          for (const day of entry.days) {
            if (day.after.shift) day.after.shift.details = { ...selectedShift.details };
          }
      dto.revision = revision.toString(16).padStart(64, "0");
      dto.employees = dto.employees.map((entry) => ({
        ...entry,
        employee: employee(company, Number(entry.employee.id.slice(-12)) - 500),
      }));
      if (unchanged) {
        dto.changeCount = 0;
        for (const entry of dto.employees)
          for (const day of entry.days) {
            day.changed = false;
            day.before = { ...day.after };
            day.overrideActive = selection.mode !== "CALENDAR";
            day.expectedVersion = selection.mode === "CALENDAR" ? 3 : day.after.originVersion;
          }
      }
      const waiting = pendingReview;
      pendingReview = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      return route.fulfill({ json: dto });
    }
    if (request.method() === "POST" && url.pathname.endsWith("/bulk")) {
      const body = request.postDataJSON() as RosterBulkApplyDto;
      const operation = request.headers()["idempotency-key"] ?? "";
      writes.push({ company, operation, body });
      const waiting = pendingSave;
      pendingSave = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      if (rejected) {
        const code = rejected;
        rejected = null;
        return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
      }
      const key = `${company}:${operation}`,
        payload = JSON.stringify(body),
        prior = receipts.get(key);
      if (prior)
        return route.fulfill(
          prior === payload
            ? { json: { id: operation, version: 0 } }
            : { status: 409, json: { code: "operation_payload_mismatch" } },
        );
      if (body.reviewRevision !== revision.toString(16).padStart(64, "0"))
        return route.fulfill({ status: 409, json: { code: "roster_bulk_changed" } });
      receipts.set(key, payload);
      commits++;
      revision++;
      if (lost) {
        lost = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: operation, version: 0 } });
    }
    reads.push(url);
    if (url.pathname.endsWith("/employees")) {
      const query = url.searchParams.get("query")?.toLowerCase() ?? "",
        after = url.searchParams.get("after") ?? "";
      const items = Array.from({ length: 55 }, (_, index) => employee(company, index + 1)).filter(
        (entry) =>
          entry.employeeNumber > after &&
          `${entry.legalName} ${entry.employeeNumber}`.toLowerCase().includes(query),
      );
      return route.fulfill({
        json: {
          items: items.slice(0, 50),
          nextCursor: items.length > 50 ? items[49]?.employeeNumber : null,
          ...(supported ? { bulkRosterSupported: true } : {}),
        },
      });
    }
    if (url.pathname.endsWith("/shifts")) {
      const query = url.searchParams.get("query")?.toLowerCase() ?? "",
        after = url.searchParams.get("after") ?? "";
      const items = shifts.filter(
        (shift) =>
          shift.details.code > after &&
          `${shift.details.name} ${shift.details.code}`.toLowerCase().includes(query),
      );
      const waiting = pendingShifts;
      pendingShifts = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      return route.fulfill({
        json: {
          items: items.slice(0, 50),
          nextCursor: items.length > 50 ? items[49]?.details.code : null,
        },
      });
    }
    return route.fallback();
  });
  return {
    identity,
    legacyApi: () => {
      supported = false;
    },
    reads,
    reviews,
    writes,
    get commits() {
      return commits;
    },
    unchanged: () => {
      unchanged = true;
    },
    loseNextSave: () => {
      lost = true;
    },
    rejectNextSave: (code: string) => {
      rejected = code;
    },
    changeRevision: () => {
      revision++;
    },
    holdReview: () => {
      const waiting = gate();
      pendingReview = waiting;
      return waiting;
    },
    holdSave: () => {
      const waiting = gate();
      pendingSave = waiting;
      return waiting;
    },
    holdShifts: () => {
      const waiting = gate();
      pendingShifts = waiting;
      return waiting;
    },
  };
}
