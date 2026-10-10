import { expect, type Page } from "@playwright/test";
import { leaveEmployeeId } from "./fixtures/leave";
import { balanceDirectoryPage, balanceLedgerPage } from "./fixtures/leave-balances";
import { companyIds } from "./identity-api";
import { installLeaveApi } from "./leave-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installLeaveBalancesApi(page: Page, permissions = ["leave.read"]) {
  const requests = await installLeaveApi(page, permissions);
  const reads: URL[] = [];
  let failure: string | null = null;
  let malformed = false;
  let held: {
    kind: "list" | "ledger";
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  await page.route("**/api/v1/companies/*/leave/employees/*/balances**", async (route) => {
    expect(route.request().method()).toBe("GET");
    expect(route.request().headers()["x-hris-client-platform"]).toBe("WEB");
    const url = new URL(route.request().url());
    reads.push(url);
    const parts = url.pathname.split("/");
    const company = parts[4];
    const employee = parts[7];
    const type = parts[9];
    const kind = type ? "ledger" : "list";
    const hold = held;
    if (hold?.kind === kind) {
      held = null;
      hold.entered.resolve();
      await hold.released.promise;
    }
    if (failure) {
      const code = failure;
      failure = null;
      return route.fulfill({
        status: code === "employee_not_found" ? 404 : 403,
        json: { code, detail: "PRIVATE SERVER DETAIL", fields: {}, parameters: {} },
      });
    }
    if (company !== companyIds[0] || employee !== leaveEmployeeId())
      return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
    expect(url.searchParams.get("limit")).toBe("20");
    const after = url.searchParams.get("after");
    if (type) {
      const raw = balanceLedgerPage(Number(parts[10]), after, type);
      return route.fulfill({
        json: malformed ? { ...raw, balance: { ...raw.balance, year: 1900 } } : raw,
      });
    }
    const raw = balanceDirectoryPage(Number(url.searchParams.get("year")), after);
    return route.fulfill({
      json: malformed ? { ...raw, employee: { ...raw.employee, id: leaveEmployeeId(1) } } : raw,
    });
  });
  return {
    requests,
    identity: requests.identity,
    reads,
    failNext: (code: string) => {
      failure = code;
    },
    malformed: () => {
      malformed = true;
    },
    hold: (kind: "list" | "ledger") => {
      const entered = gate();
      const released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
