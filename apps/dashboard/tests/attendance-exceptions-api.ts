import type { Page } from "@playwright/test";
import {
  attendanceExceptionCursorFixture,
  attendanceExceptionPageDtoFixture,
} from "../src/features/workforce/di/attendance-exception-fixture";
import type { AttendanceExceptionSearch } from "../src/features/workforce/domain/entities/attendance-exception-search";
import { installAttendanceApi } from "./attendance-api";
import { companyIds } from "./identity-api";

function gate() {
  let release!: () => void, enter!: () => void;
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

/** Transport fixture; independent review transitions are exercised against the real API. */
export async function installAttendanceExceptionsApi(
  page: Page,
  options: { count?: number; permissions?: readonly string[] } = {},
) {
  const attendance = await installAttendanceApi(page, {
    permissions: options.permissions ?? ["workforce.read"],
  });
  const reads: URL[] = [];
  let held: ReturnType<typeof gate> | null = null,
    rejected: string | null = null,
    resolved = false;
  await page.route("**/api/v1/companies/*/workforce/attendance/exceptions?**", async (route) => {
    const url = new URL(route.request().url());
    reads.push(url);
    const company = url.pathname.split("/")[4] ?? "";
    const filter = url.searchParams.get("kind");
    const search: AttendanceExceptionSearch = {
      from: url.searchParams.get("from") ?? "",
      until: url.searchParams.get("until") ?? "",
      kind: filter === "PENDING" || filter === "INCOMPLETE" ? filter : null,
      after: url.searchParams.get("after"),
    };
    const snapshot = attendanceExceptionPageDtoFixture(
      search,
      resolved ? 0 : (options.count ?? 53),
    );
    const last = snapshot.items.at(-1);
    if (snapshot.nextCursor && last)
      snapshot.nextCursor = attendanceExceptionCursorFixture(search, last, company);
    if (company === companyIds[1])
      snapshot.items = snapshot.items.map((item) => ({
        ...item,
        employeeName: `South ${item.employeeName}`,
      }));
    const failure = rejected,
      waiting = held;
    rejected = null;
    held = null;
    if (waiting) {
      waiting.enter();
      await waiting.held;
    }
    return route.fulfill(
      failure
        ? { status: 403, json: { code: failure, detail: "PRIVATE TECHNICAL ERROR" } }
        : { json: snapshot },
    );
  });
  return {
    identity: attendance.identity,
    reads,
    rejectNext: (code: string) => {
      rejected = code;
    },
    resolveAll: () => {
      resolved = true;
    },
    holdRead: () => {
      held = gate();
      return held;
    },
  };
}
