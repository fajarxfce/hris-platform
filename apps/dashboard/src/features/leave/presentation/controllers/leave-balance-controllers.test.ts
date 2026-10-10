import { expect, it, vi } from "vitest";
import { leaveEmployeeId } from "../../../../../tests/fixtures/leave";
import {
  balanceDirectoryPage,
  balanceEntryId,
  balanceLedgerPage,
  balanceTypeId,
} from "../../../../../tests/fixtures/leave-balances";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import { toEmployeeLeaveBalances } from "../../data/mappers/leave-balance-mapper";
import { toLeaveLedger } from "../../data/mappers/leave-ledger-mapper";
import { LeaveBalancesController } from "./leave-balances-controller";
import { LeaveLedgerController } from "./leave-ledger-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read"] };
const query = { year: "2026", after: null };
const page = toEmployeeLeaveBalances(
  balanceDirectoryPage(),
  companyId,
  leaveEmployeeId(),
  2026,
  null,
);
const ledger = toLeaveLedger(
  balanceLedgerPage(),
  companyId,
  leaveEmployeeId(),
  balanceTypeId(),
  2026,
  null,
);
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function harness(kind: "balances" | "ledger") {
  const result = success(kind === "balances" ? page : ledger);
  const load = vi.fn().mockResolvedValue(result);
  const controller =
    kind === "balances"
      ? new LeaveBalancesController({ execute: load }, access, leaveEmployeeId(), query)
      : new LeaveLedgerController(
          { execute: load },
          access,
          leaveEmployeeId(),
          balanceTypeId(),
          query,
        );
  return { controller, load, result };
}

it.each(["balances", "ledger"] as const)(
  "%s refresh cancels stale work and clears private values after current denial",
  async (kind) => {
    const { controller, load, result } = harness(kind);
    controller.activate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    expect(load).toHaveBeenCalledTimes(1);
    const held = deferred<typeof result>();
    load.mockReturnValueOnce(held.promise);
    const reading = controller.refresh();
    const signal = load.mock.lastCall?.at(-1) as AbortSignal;
    expect(controller.getSnapshot()).toMatchObject(
      kind === "balances" ? { page: null } : { ledger: null, selected: null },
    );
    load.mockResolvedValueOnce(failed("employee_not_found"));
    await controller.refresh();
    held.resolve(result);
    await reading;
    expect(signal.aborted).toBe(true);
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      failure: { code: "employee_not_found" },
    });
    expect(controller.getSnapshot()).toMatchObject(
      kind === "balances" ? { page: null } : { ledger: null },
    );
    controller.deactivate();
  },
);

for (const kind of ["balances", "ledger"] as const)
  it.each(["value", "error"] as const)(
    `${kind} disposal drops a late %s and releases observation`,
    async (outcome) => {
      const { controller, load, result } = harness(kind);
      const held = deferred<typeof result>();
      load.mockReturnValue(held.promise);
      const listener = vi.fn();
      const unsubscribe = controller.subscribe(listener);
      controller.activate();
      const reading = controller.refresh();
      const signal = load.mock.lastCall?.at(-1) as AbortSignal;
      unsubscribe();
      const observed = listener.mock.calls.length;
      controller.deactivate();
      if (outcome === "value") held.resolve(result);
      else held.reject(new Error("PRIVATE LATE FAILURE"));
      await reading;
      expect(signal.aborted).toBe(true);
      expect(listener).toHaveBeenCalledTimes(observed);
      expect(controller.getSnapshot()).toMatchObject({ stage: "loading", failure: null });
      await controller.refresh();
      expect(load).toHaveBeenCalledTimes(2);
      load.mockResolvedValueOnce(result);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      controller.deactivate();
    },
  );

it.each(["balances", "ledger"] as const)(
  "%s verification failure does not trigger an automatic retry",
  async (kind) => {
    const { controller, load, result } = harness(kind);
    load.mockResolvedValueOnce(failed("mfa_required"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().failure?.code).toBe("mfa_required"));
    controller.activate();
    expect(load).toHaveBeenCalledTimes(1);
    load.mockResolvedValueOnce(result);
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("ready");
    controller.deactivate();
  },
);

it("movement selection is limited to loaded evidence and clears on a new observation", async () => {
  const load = vi.fn().mockResolvedValue(success(ledger));
  const controller = new LeaveLedgerController(
    { execute: load },
    access,
    leaveEmployeeId(),
    balanceTypeId(),
    query,
  );
  controller.select(balanceEntryId());
  expect(controller.getSnapshot().selected).toBeNull();
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  controller.select(balanceEntryId());
  expect(controller.getSnapshot().selected).toBe(ledger.entries[0]);
  controller.select(balanceEntryId(23));
  expect(controller.getSnapshot().selected).toBe(ledger.entries[0]);
  expect(load).toHaveBeenCalledTimes(1);
  const held = deferred<ReturnType<typeof success<typeof ledger>>>();
  load.mockReturnValueOnce(held.promise);
  const reading = controller.refresh();
  expect(controller.getSnapshot().selected).toBeNull();
  controller.select(balanceEntryId());
  expect(controller.getSnapshot().selected).toBeNull();
  held.resolve(success(ledger));
  await reading;
  expect(controller.getSnapshot().selected).toBeNull();
  controller.deactivate();
});
