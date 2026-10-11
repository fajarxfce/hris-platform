import type { Page } from "@playwright/test";
import type {
  HolidayChangeDto,
  HolidayDto,
} from "../src/features/workforce/data/models/holiday-dto";
import { holidayDtoFixture, identifier } from "../src/features/workforce/di/holiday-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export const newYearId = identifier();
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
export async function installHolidaysApi(
  page: Page,
  options: { permissions?: readonly string[] } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["company.read", "workforce.manage"],
  });
  const records = new Map<string, HolidayDto>();
  for (const company of companyIds) {
    records.set(`${company}:${newYearId}`, {
      ...holidayDtoFixture(1, 2),
      name: company === companyIds[0] ? "North holiday" : "South holiday",
    });
    records.set(`${company}:${identifier(2)}`, {
      ...holidayDtoFixture(2),
      workDate: "2026-12-25",
      name: "Year-end holiday",
    });
  }
  const reads: URL[] = [];
  const writes: {
    company: string;
    id: string;
    operation: string | undefined;
    csrf: string | undefined;
    body: HolidayChangeDto;
  }[] = [];
  const receipts = new Map<string, { payload: string; receipt: { id: string; version: number } }>();
  let lost = false;
  let rejected: string | null = null;
  let held: ReturnType<typeof gate> | null = null;
  let commits = 0;
  await page.route("**/api/v1/companies/*/workforce/holidays**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[7];
    if (request.method() === "PUT" && id) {
      const body = request.postDataJSON() as HolidayChangeDto;
      const operation = request.headers()["idempotency-key"];
      writes.push({ company, id, operation, body, csrf: request.headers()["x-csrf-token"] });
      const waiting = held;
      held = null;
      if (waiting) {
        waiting.enter();
        await waiting.held;
      }
      if (rejected) {
        const code = rejected;
        rejected = null;
        return route.fulfill({ status: 409, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
      }
      const key = `${company}:${operation}`;
      const payload = JSON.stringify({ id, body });
      const prior = receipts.get(key);
      if (prior)
        return route.fulfill(
          prior.payload === payload
            ? { json: prior.receipt }
            : { status: 409, json: { code: "operation_payload_mismatch" } },
        );
      const current = records.get(`${company}:${id}`);
      if ((current?.version ?? null) !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_version" } });
      if (
        [...records.entries()].some(
          ([recordKey, value]) =>
            recordKey.startsWith(`${company}:`) &&
            value.id !== id &&
            value.workDate === body.workDate,
        )
      )
        return route.fulfill({ status: 409, json: { code: "data_conflict" } });
      const version = body.expectedVersion === null ? 0 : body.expectedVersion + 1;
      records.set(`${company}:${id}`, {
        id,
        version,
        name: body.name,
        workDate: body.workDate,
        active: body.active,
      });
      const receipt = { id, version };
      receipts.set(key, { payload, receipt });
      commits += 1;
      if (lost) {
        lost = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: receipt });
    }
    reads.push(url);
    if (id) {
      const dto = records.get(`${company}:${id}`);
      return route.fulfill(
        dto ? { json: dto } : { status: 404, json: { code: "holiday_not_found" } },
      );
    }
    return route.fulfill({
      json: [...records.entries()]
        .filter(([key]) => key.startsWith(`${company}:`))
        .map(([, dto]) => dto)
        .filter(
          (dto) =>
            dto.workDate >= (url.searchParams.get("from") ?? "") &&
            dto.workDate <= (url.searchParams.get("until") ?? ""),
        )
        .sort((a, b) => (a.workDate < b.workDate ? -1 : 1)),
    });
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
      rejected = code;
    },
    holdSave: () => {
      const waiting = gate();
      held = waiting;
      return waiting;
    },
    replaceHoliday: (name: string, workDate: string, version: number) => {
      const key = `${companyIds[0]}:${newYearId}`;
      const current = records.get(key);
      if (current) records.set(key, { ...current, name, workDate, version });
    },
  };
}
