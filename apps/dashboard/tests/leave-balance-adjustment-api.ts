import { expect, type Page } from "@playwright/test";
import type { LeaveBalanceAdjustmentDto } from "../src/features/leave/data/models/leave-balance-adjustment-dto";
import type { LeaveLedgerDto } from "../src/features/leave/data/models/leave-ledger-dto";
import { leaveEmployeeId } from "./fixtures/leave";
import { balanceEntryId, balanceLedger } from "./fixtures/leave-balances";
import { leavePolicyId } from "./fixtures/leave-policies";
import { companyIds } from "./identity-api";
import { installLeavePoliciesApi } from "./leave-policies-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installLeaveBalanceAdjustmentApi(
  page: Page,
  permissions = ["leave.read", "leave.manage"],
) {
  const api = await installLeavePoliciesApi(page, permissions);
  const balances = new Map<string, LeaveLedgerDto>();
  const balanceReads: URL[] = [];
  const writes: {
    company: string;
    employee: string;
    type: string;
    year: number;
    operation: string;
    body: LeaveBalanceAdjustmentDto;
  }[] = [];
  const receipts = new Map<string, { id: string; payload: string }>();
  let rejection: string | null = null;
  let drop = false;
  let held: {
    kind: "read" | "write";
    entered: ReturnType<typeof gate>;
    released: ReturnType<typeof gate>;
  } | null = null;
  function record(year = 2026, type = leavePolicyId()) {
    const key = `${type}:${year}`;
    const existing = balances.get(key);
    if (existing) return existing;
    const policy = api.records
      .get(companyIds[0])
      ?.find((item) => item.current.id === type)?.current;
    if (!policy) throw new Error("Unknown fixture type");
    const raw = balanceLedger(year);
    const funded = year !== 2027 && type === leavePolicyId();
    const value: LeaveLedgerDto = {
      ...raw,
      typeId: type,
      typeCode: policy.code,
      typeName: policy.name,
      balance: funded
        ? raw.balance
        : {
            accountId: null,
            year,
            availableDays: "0",
            reservedDays: "0",
            consumedDays: "0",
            closed: false,
            version: 0,
          },
      entries: funded ? raw.entries : { items: [], nextCursor: null },
      availableActions: permissions.includes("leave.manage") && year !== 2025 ? ["ADJUST"] : [],
    };
    balances.set(key, value);
    return value;
  }
  await page.route("**/api/v1/companies/*/leave/employees/*/balances**", async (route) => {
    const request = route.request();
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    const url = new URL(request.url());
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    const employee = parts[7] ?? "";
    const type = parts[9];
    const year = Number(type ? parts[10] : url.searchParams.get("year"));
    if (company !== companyIds[0] || employee !== leaveEmployeeId())
      return route.fulfill({ status: 404, json: { code: "employee_not_found" } });
    const kind = request.method() === "POST" ? "write" : "read";
    if (kind === "read") balanceReads.push(url);
    const body = kind === "write" ? (request.postDataJSON() as LeaveBalanceAdjustmentDto) : null;
    const operation = request.headers()["idempotency-key"] ?? "";
    if (body) {
      expect(type).toBeTruthy();
      expect(parts[11]).toBe("adjustments");
      expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
      writes.push({ company, employee, type: type ?? "", year, operation, body });
    }
    const hold = held;
    if (hold?.kind === kind) {
      held = null;
      hold.entered.resolve();
      await hold.released.promise;
    }
    if (body && type) {
      if (rejection) {
        const code = rejection;
        rejection = null;
        return route.fulfill({ status: 409, json: { code, detail: "PRIVATE SERVER DETAIL" } });
      }
      const payload = JSON.stringify({ company, employee, type, year, body });
      const previous = receipts.get(operation);
      if (previous) {
        expect(previous.payload).toBe(payload);
        return route.fulfill({ json: { id: previous.id, version: 0 } });
      }
      const current = record(year, type);
      if (current.balance.version !== body.expectedVersion)
        return route.fulfill({ status: 409, json: { code: "stale_balance_version" } });
      if (current.balance.closed)
        return route.fulfill({ status: 409, json: { code: "leave_year_closed" } });
      const available = Number(current.balance.availableDays) + Number(body.days);
      if (available < 0)
        return route.fulfill({ status: 409, json: { code: "insufficient_leave_balance" } });
      const id = balanceEntryId(100 + receipts.size);
      current.balance = {
        ...current.balance,
        accountId: current.balance.accountId ?? balanceEntryId(1000),
        availableDays: String(available),
        version: current.balance.version + 1,
      };
      current.entries.items.unshift({
        id,
        sourceId: id,
        requestId: null,
        actorId: "20000000-0000-4000-8000-000000000001",
        kind: "ADJUSTMENT",
        availableDeltaDays: body.days,
        reservedDeltaDays: "0",
        consumedDeltaDays: "0",
        recordedAt: new Date(Date.UTC(2026, 9, 11, 2, 0, receipts.size)).toISOString(),
        reason: body.reason,
      });
      receipts.set(operation, { id, payload });
      if (drop) {
        drop = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id, version: 0 } });
    }
    expect(request.method()).toBe("GET");
    expect(url.searchParams.get("limit")).toBe("20");
    const after = url.searchParams.get("after");
    if (type) {
      const current = record(year, type);
      const start =
        after === null ? 0 : current.entries.items.findIndex((item) => item.id === after) + 1;
      const entries = current.entries.items.slice(start);
      return route.fulfill({
        json: {
          ...current,
          entries: {
            items: entries.slice(0, 20),
            nextCursor: entries.length > 20 ? entries[19]?.id : null,
          },
        },
      });
    }
    const employeeRecord = record(year);
    const items = [...balances.values()]
      .filter(
        (item) =>
          item.balance.year === year &&
          item.balance.accountId !== null &&
          (after === null || item.typeCode > after),
      )
      .sort((left, right) => left.typeCode.localeCompare(right.typeCode))
      .map(({ balance, typeId, typeCode, typeName }) => ({ balance, typeId, typeCode, typeName }));
    return route.fulfill({
      json: {
        employee: employeeRecord.employee,
        year,
        balances: {
          items: items.slice(0, 20),
          nextCursor: items.length > 20 ? items[19]?.typeCode : null,
        },
      },
    });
  });
  return {
    ...api,
    balanceReads,
    writes,
    receipts,
    record,
    dropNext: () => {
      drop = true;
    },
    rejectNext: (code: string) => {
      rejection = code;
    },
    advance: () => {
      const current = record();
      current.balance = {
        ...current.balance,
        availableDays: "9",
        version: current.balance.version + 1,
      };
    },
    holdBalance: (kind: "read" | "write") => {
      const entered = gate();
      const released = gate();
      held = { kind, entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
