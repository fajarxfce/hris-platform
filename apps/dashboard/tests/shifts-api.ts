import type { Page } from "@playwright/test";
import type { ShiftChangeDto, ShiftDto } from "../src/features/workforce/data/models/shift-dto";
import { identifier, shiftDtoFixture } from "../src/features/workforce/di/workforce-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export const dayShiftId = identifier();
function gate() {
  let release!: () => void;
  let entered!: () => void;
  return {
    held: new Promise<void>((done) => {
      release = done;
    }),
    started: new Promise<void>((done) => {
      entered = done;
    }),
    release: () => release(),
    enter: () => entered(),
  };
}
export async function installShiftsApi(
  page: Page,
  options: { permissions?: readonly string[]; many?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["company.read", "workforce.read", "workforce.manage"],
  });
  const records = new Map<string, ShiftDto>();
  for (const company of companyIds) {
    const dto = shiftDtoFixture(1, 2);
    records.set(`${company}:${dayShiftId}`, {
      ...dto,
      details: {
        ...dto.details,
        code: "DAY",
        name: company === companyIds[0] ? "North day" : "South day",
        startsAt: "08:00:00",
        endsAt: "17:00:00",
        breakMinutes: 60,
        locationRequired: false,
        fence: null,
      },
    });
    if (options.many)
      for (let n = 2; n < 57; n += 1)
        records.set(`${company}:${identifier(n)}`, shiftDtoFixture(n));
  }
  const writes: {
    company: string;
    id: string;
    operation: string | undefined;
    csrf: string | undefined;
    body: ShiftChangeDto;
  }[] = [];
  const reads: URL[] = [];
  const receipts = new Map<string, { payload: string; receipt: { id: string; version: number } }>();
  let lost = false;
  let rejected: string | null = null;
  let held: ReturnType<typeof gate> | null = null;
  let commits = 0;
  await page.route("**/api/v1/companies/*/workforce/shifts**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const company = url.pathname.split("/")[4] ?? "";
    const id = url.pathname.split("/")[7];
    if (request.method() === "PUT" && id) {
      const body = request.postDataJSON() as ShiftChangeDto;
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
      const { expectedVersion, active, reason: _reason, ...details } = body;
      const version = expectedVersion === null ? 0 : expectedVersion + 1;
      records.set(`${company}:${id}`, { id, version, active, details });
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
        dto ? { json: dto } : { status: 404, json: { code: "shift_not_found" } },
      );
    }
    const query = url.searchParams;
    const items = [...records.entries()]
      .filter(([key]) => key.startsWith(`${company}:`))
      .map(([, dto]) => dto)
      .filter(
        (dto) =>
          `${dto.details.code} ${dto.details.name}`
            .toLowerCase()
            .includes((query.get("query") ?? "").toLowerCase()) &&
          (!query.has("after") || dto.details.code > (query.get("after") ?? "")),
      )
      .sort((a, b) => (a.details.code < b.details.code ? -1 : 1));
    return route.fulfill({
      json: {
        items: items.slice(0, 50),
        nextCursor: items.length > 50 ? (items[49]?.details.code ?? null) : null,
      },
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
    replaceShift: (name: string, version: number) => {
      const key = `${companyIds[0]}:${dayShiftId}`;
      const current = records.get(key);
      if (current) records.set(key, { ...current, version, details: { ...current.details, name } });
    },
  };
}
