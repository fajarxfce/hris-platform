import { expect, type Page } from "@playwright/test";
import type {
  LeaveAccrualScheduleDetailsDto,
  LeaveAccrualScheduleReviewDto,
} from "../src/features/leave/data/models/leave-accrual-schedule-dto";
import {
  accrualReviewWire,
  accrualWire,
} from "../src/features/leave/di/leave-accrual-schedule-fixture";
import { deferred } from "../src/features/leave/di/leave-batch-fixture";
import { companyIds } from "./identity-api";
import { batchPermissions, installLeaveBatchesApi } from "./leave-batches-api";

type ReadKind = "review" | "detail" | "list";
export async function installLeaveAccrualScheduleApi(page: Page, permissions = batchPermissions) {
  const batches = await installLeaveBatchesApi(page, permissions),
    records = new Map<string, LeaveAccrualScheduleDetailsDto[]>(companyIds.map((id) => [id, []])),
    reads: { kind: ReadKind; url: URL }[] = [],
    writes: { company: string; operation: string; body: Record<string, unknown> }[] = [],
    receipts = new Map<string, { body: string; value: { id: string; version: number } }>();
  let lost = false,
    committed = 0,
    delivered = 0;
  let commandFailure: { code: string; status: number } | null = null,
    readFailure: { kind: ReadKind; code: string; status: number } | null = null;
  type Gate = {
    kind: ReadKind | "write";
    entered: ReturnType<typeof deferred<void>>;
    released: ReturnType<typeof deferred<void>>;
  };
  let gate: Gate | null = null;
  function seed(company: string = companyIds[0], value = structuredClone(accrualWire)) {
    records.get(company)?.push(value);
    for (const period of value.periods) {
      // Scheduled periods and the reused recovery fixture own disjoint job identities.
      period.batch.jobId = period.batch.jobId.replace("-0000-4000-", "-abcd-4000-");
      const item = batches.seed();
      item.batch = structuredClone(period.batch);
      item.job = {
        ...item.job,
        jobId: item.batch.jobId,
        status: period.jobStatus,
        completedItems: 0,
        totalItems: item.batch.totalEmployees + 1,
        attempts: 0,
        version: 0,
        cancellationRequested: false,
        failureCode: null,
      };
      item.results = { items: [], nextCursor: null };
      item.employees = [];
      item.counts = { applied: 0, unchanged: 0, skipped: 0, failed: 0, completed: 0 };
      item.availableActions = [];
      item.attempts = [
        {
          number: 1,
          jobId: item.batch.jobId,
          baseCompleted: 0,
          startedAt: item.batch.createdAt,
          reason: item.batch.reason,
        },
      ];
      batches.records.get(company)?.push(item);
    }
    return value;
  }
  function review(url: URL): LeaveAccrualScheduleReviewDto {
    const type = batches.types.find((item) => item.id === url.searchParams.get("typeId"));
    if (!type) throw Error("Missing reviewed type fixture");
    const first = url.searchParams.get("firstPeriod") ?? "",
      count = Number(url.searchParams.get("periods"));
    const frequency = type.accrual?.frequency === "ANNUAL" ? "ANNUAL" : "MONTHLY";
    return {
      ...structuredClone(accrualReviewWire),
      frequency,
      policyVersion: type.version,
      periods: Array.from({ length: count }, (_, index) => {
        const month = new Date(`${first}-01T00:00:00Z`);
        month.setUTCMonth(month.getUTCMonth() + index * (frequency === "ANNUAL" ? 12 : 1));
        const period = month.toISOString().slice(0, 7);
        if (frequency === "MONTHLY") month.setUTCMonth(month.getUTCMonth() + 1);
        month.setUTCHours(-7);
        return {
          period,
          dueAt: month.toISOString(),
          type: { ...structuredClone(type), appliedRevision: 2 },
        };
      }),
    };
  }
  await page.route(
    /\/api\/v1\/companies\/[^/]+\/leave\/accrual-schedules(?:[/?].*)?$/u,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        company = url.pathname.split("/")[4] ?? "",
        id = url.pathname.split("/")[7];
      const reject = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, fields: {}, parameters: {}, detail: "PRIVATE SCHEDULE DIAGNOSTIC" },
        });
      const all = records.get(company);
      if (!all) return reject("company_access_denied", 403);
      if (request.method() === "GET") {
        const kind: ReadKind = id === "review" ? "review" : id ? "detail" : "list";
        reads.push({ kind, url });
        const rejected = readFailure?.kind === kind ? readFailure : null;
        let payload: unknown;
        if (rejected)
          payload = {
            code: rejected.code,
            fields: {},
            parameters: {},
            detail: "PRIVATE SCHEDULE DIAGNOSTIC",
          };
        else if (kind === "review") payload = review(url);
        else if (kind === "detail") {
          const item = all.find((item) => item.schedule.id === id);
          if (!item) return reject("leave_accrual_schedule_not_found", 404);
          payload = {
            ...item,
            periods: item.periods.map((period) => {
              const current = batches.records
                .get(company)
                ?.find((item) => item.batch.id === period.batch.id);
              if (!current) throw Error("Missing period fixture");
              return {
                ...period,
                batch: current.batch,
                jobStatus: current.job.status,
                completedEmployees: current.counts.completed,
              };
            }),
          };
        } else {
          expect(url.searchParams.get("limit")).toBe("20");
          const after = url.searchParams.get("after"),
            items = all
              .map((item) => item.schedule)
              .sort((a, b) => a.id.localeCompare(b.id))
              .filter((item) => after === null || item.id > after);
          payload = {
            items: items.slice(0, 20),
            nextCursor: items.length > 20 ? items[19]?.id : null,
          };
        }
        const snapshot = structuredClone(payload),
          held = gate?.kind === kind ? gate : null;
        if (held) {
          gate = null;
          held.entered.resolve();
          await held.released.promise;
        }
        await route.fulfill({ status: rejected?.status ?? 200, json: snapshot });
        delivered++;
        return;
      }
      expect(request.method()).toBe("POST");
      const body = request.postDataJSON() as Record<string, unknown>,
        operation = request.headers()["idempotency-key"] ?? "";
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      expect(request.headers()["x-csrf-token"]).toBeTruthy();
      expect(Object.keys(body).sort()).toEqual(
        [
          "id",
          "typeId",
          "firstPeriod",
          "periods",
          "employeeIds",
          "expectedPolicyVersion",
          "reason",
        ].sort(),
      );
      writes.push({ company, operation, body });
      const held = gate?.kind === "write" ? gate : null;
      if (held) {
        gate = null;
        held.entered.resolve();
        await held.released.promise;
      }
      if (commandFailure) {
        const failed = commandFailure;
        commandFailure = null;
        return reject(failed.code, failed.status);
      }
      const key = `${company}:${operation}`,
        existing = receipts.get(key);
      if (existing) {
        expect(JSON.stringify(body)).toBe(existing.body);
        return route.fulfill({ json: existing.value });
      }
      const selected = new URL(url);
      for (const field of ["typeId", "firstPeriod", "periods"])
        selected.searchParams.set(field, String(body[field]));
      const reviewed = review(selected);
      if (reviewed.policyVersion !== body.expectedPolicyVersion)
        return reject("leave_batch_policy_changed");
      expect(body.employeeIds).toEqual(reviewed.employeeIds);
      const value = structuredClone(accrualWire);
      value.schedule = {
        ...value.schedule,
        id: String(body.id),
        typeId: String(body.typeId),
        policyVersion: reviewed.policyVersion,
        frequency: reviewed.frequency,
        firstPeriod: String(body.firstPeriod),
        periodCount: Number(body.periods),
        reason: String(body.reason),
      };
      value.periods = reviewed.periods.map((period, index) => {
        const original = accrualWire.periods[0];
        if (!original) throw Error("Missing scheduled period");
        const scheduledFor = new Date(
          Math.max(Date.parse(value.schedule.createdAt), Date.parse(period.dueAt)),
        ).toISOString();
        return {
          ...structuredClone(original),
          scheduledFor,
          batch: {
            ...structuredClone(original.batch),
            id: `40000000-abcd-4000-8000-${String(committed * 12 + index + 1).padStart(12, "0")}`,
            jobId: `60000000-abcd-4000-8000-${String(committed * 12 + index + 1).padStart(12, "0")}`,
            period: period.period,
            policyVersion: reviewed.policyVersion,
            typeId: String(body.typeId),
            reason: String(body.reason),
            policy: {
              ...original.batch.policy,
              typeId: String(body.typeId),
              code: period.type.code,
              name: period.type.name,
              revision: period.type.appliedRevision,
              accrual: period.type.accrual,
            },
          },
        };
      });
      seed(company, value);
      committed++;
      const receipt = { id: value.schedule.id, version: 0 };
      receipts.set(key, { body: JSON.stringify(body), value: receipt });
      if (lost) {
        lost = false;
        return route.abort("connectionfailed");
      }
      return route.fulfill({ json: receipt });
    },
  );
  return {
    batches,
    identity: batches.identity,
    records,
    reads,
    writes,
    seed,
    get committed() {
      return committed;
    },
    get delivered() {
      return delivered;
    },
    loseCommand: () => {
      lost = true;
    },
    rejectCommand: (code: string, status = 409) => {
      commandFailure = { code, status };
    },
    rejectRead: (kind: ReadKind, code: string | null, status = 403) => {
      readFailure = code ? { kind, code, status } : null;
    },
    hold: (kind: ReadKind | "write") => {
      const entered = deferred<void>(),
        released = deferred<void>();
      gate = { kind, entered, released };
      return { entered: entered.promise, release: () => released.resolve() };
    },
  };
}
