import { expect, type Page } from "@playwright/test";
import type {
  LeaveCalendarDto,
  LeaveCalendarEntriesDto,
} from "../src/features/leave/data/models/leave-calendar-dto";
import { calendarDeferred } from "../src/features/leave/di/leave-calendar-fixture";
import { companyIds } from "./identity-api";
import { installLeaveApi } from "./leave-api";

type Entry = LeaveCalendarEntriesDto["entries"]["items"][number];
type Kind = "month" | "date";
export async function installLeaveCalendarApi(
  page: Page,
  permissions = ["leave.read", "leave.approve"],
) {
  const requests = await installLeaveApi(page, permissions);
  const records = new Map<string, Map<string, Entry[]>>();
  for (const company of companyIds) {
    const all = (requests.records.get(company) ?? []).map(
      (item): Entry => ({
        requestId: item.id,
        employeeId: item.employeeId,
        employeeNumber: item.employeeNumber,
        employeeName: item.employeeName,
        typeCode: item.policy.code,
        typeName: item.policy.name,
        portion: "FIRST_HALF",
        status: item.status === "APPROVED" ? "APPROVED" : "PENDING",
        version: item.version,
      }),
    );
    const first = all[0],
      second = all[1];
    records.set(
      company,
      new Map([
        ["2026-10-15", all],
        [
          "2026-10-05",
          first && second
            ? [
                first,
                {
                  ...second,
                  employeeId: first.employeeId,
                  employeeNumber: first.employeeNumber,
                  employeeName: first.employeeName,
                  portion: "SECOND_HALF",
                },
              ]
            : [],
        ],
      ]),
    );
  }
  const reads: { kind: Kind; url: URL }[] = [];
  let companyDate = "2026-10-15";
  let rejection: { kind: Kind; code: string; status: number } | null = null;
  let hold: {
    kind: Kind;
    entered: ReturnType<typeof calendarDeferred<void>>;
    released: ReturnType<typeof calendarDeferred<void>>;
  } | null = null;
  let delivered = 0;
  await page.route("**/api/v1/companies/*/leave/calendar**", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url()),
      company = url.pathname.split("/")[4] ?? "",
      kind = url.pathname.endsWith("/entries") ? "date" : "month";
    reads.push({ kind, url });
    const scope = url.searchParams.get("scope") === "TEAM" ? "TEAM" : "COMPANY";
    const source = records.get(company) ?? new Map<string, Entry[]>(),
      team = requests.records.get(company)?.[0]?.employeeId;
    const selected = (entries: Entry[]) =>
      entries.filter((entry) => scope === "COMPANY" || entry.employeeId === team);
    let json: unknown;
    const denied = rejection?.kind === kind ? rejection : null;
    if (denied)
      json = { code: denied.code, fields: {}, parameters: {}, detail: "PRIVATE TECHNICAL MESSAGE" };
    else if (kind === "month") {
      const month = url.searchParams.get("month") ?? companyDate.slice(0, 7);
      json = {
        month,
        scope,
        companyDate,
        timezone: "Asia/Jakarta",
        days: [...source.entries()]
          .filter(([date]) => date.startsWith(`${month}-`))
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([date, entries]) => {
            const items = selected(entries);
            return {
              date,
              employeeCount: new Set(items.map((entry) => entry.employeeId)).size,
              requestCount: items.length,
              pendingCount: items.filter((entry) => entry.status === "PENDING").length,
              approvedCount: items.filter((entry) => entry.status === "APPROVED").length,
              cancellationPendingCount: items.filter(
                (entry) => entry.status === "CANCELLATION_PENDING",
              ).length,
            };
          })
          .filter((day) => day.requestCount > 0),
      } satisfies LeaveCalendarDto;
    } else {
      expect(url.searchParams.get("limit")).toBe("20");
      const date = url.searchParams.get("date") ?? "",
        after = url.searchParams.get("after");
      const all = selected(source.get(date) ?? [])
        .filter((entry) => after === null || entry.requestId > after)
        .sort((a, b) => a.requestId.localeCompare(b.requestId));
      json = {
        date,
        scope,
        companyDate,
        timezone: "Asia/Jakarta",
        entries: {
          items: all.slice(0, 20),
          nextCursor: all.length > 20 ? (all[19]?.requestId ?? null) : null,
        },
      } satisfies LeaveCalendarEntriesDto;
    }
    const response = structuredClone(json),
      gate = hold?.kind === kind ? hold : null;
    if (gate) {
      hold = null;
      gate.entered.resolve();
      await gate.released.promise;
    }
    await route.fulfill({ json: response, status: denied?.status ?? 200 });
    delivered++;
  });
  return {
    requests,
    identity: requests.identity,
    records,
    reads,
    get delivered() {
      return delivered;
    },
    date: (value: string) => {
      companyDate = value;
    },
    reject: (kind: Kind, code: string | null, status = 403) => {
      rejection = code === null ? null : { kind, code, status };
    },
    hold: (kind: Kind) => {
      const entered = calendarDeferred<void>(),
        released = calendarDeferred<void>();
      hold = { kind, entered, released };
      return { entered: entered.promise, release: () => released.resolve() };
    },
  };
}
