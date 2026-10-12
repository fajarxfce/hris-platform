import type { Page } from "@playwright/test";
import { attendanceCorrectionDtoFixture } from "../src/features/workforce/di/attendance-correction-fixture";
import { installAttendanceApi } from "./attendance-api";

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

/** History transport only; correction writes and their authority are tested against the API. */
export async function installAttendanceHistoryApi(page: Page, count = 53) {
  if (count < 0 || count > 200) throw new Error("Invalid history fixture size");
  const attendance = await installAttendanceApi(page, { permissions: ["workforce.read"] });
  const records = Array.from({ length: count }, (_, index) =>
    attendanceCorrectionDtoFixture(count - index - 1),
  );
  const reads: URL[] = [];
  let waiting: ReturnType<typeof gate> | null = null;
  let rejected: string | null = null;
  await page.route(
    "**/api/v1/companies/*/workforce/employees/*/attendance/corrections?**",
    async (route) => {
      if (route.request().method() !== "GET") return route.fallback();
      const url = new URL(route.request().url());
      reads.push(url);
      const after = url.searchParams.get("after");
      const filtered = records.filter((item) => after === null || item.version < Number(after));
      const selected = filtered.slice(0, 50);
      const snapshot = structuredClone({
        items: selected,
        nextCursor: filtered.length > 50 ? String(selected.at(-1)?.version) : null,
      });
      const failure = rejected,
        held = waiting;
      rejected = null;
      waiting = null;
      if (held) {
        held.enter();
        await held.held;
      }
      return route.fulfill(
        failure
          ? { status: 403, json: { code: failure, detail: "PRIVATE TECHNICAL ERROR" } }
          : { json: snapshot },
      );
    },
  );
  return {
    identity: attendance.identity,
    reads,
    rejectNext: (code: string) => {
      rejected = code;
    },
    holdRead: () => {
      waiting = gate();
      return waiting;
    },
    appendCorrection: () => {
      records.unshift(attendanceCorrectionDtoFixture(records.length));
    },
  };
}
