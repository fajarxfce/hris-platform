import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type {
  PayrollComparisonAmounts,
  PayrollRunComparison,
} from "../../domain/entities/payroll-run-comparison";
import type { PayrollRunComparisonDto } from "../models/payroll-run-comparison-dto";

const keys = ["taxableGross", "withheld", "takeHome"] as const;
const minor = (v: string) => BigInt(v.replace(".", ""));
function check(condition: boolean): asserts condition {
  if (!condition) throw new InvalidHttpResponseError();
}
function checkAmounts(value: PayrollComparisonAmounts, count: number) {
  const n = BigInt(count);
  const g = minor(value.taxableGross),
    w = minor(value.withheld),
    h = minor(value.takeHome);
  check(
    g >= 0n &&
      g <= 5_000_000_000_000n * n &&
      w >= -60_000_000_000_000n * n &&
      w <= 5_000_000_000_000n * n &&
      h >= 0n &&
      h <= 65_000_000_000_000n * n,
  );
}
function checkDelta(
  baseline: PayrollComparisonAmounts | null,
  current: PayrollComparisonAmounts | null,
  delta: PayrollComparisonAmounts,
) {
  for (const key of keys)
    check(
      minor(delta[key]) ===
        (current ? minor(current[key]) : 0n) - (baseline ? minor(baseline[key]) : 0n),
    );
}
export function toPayrollRunComparison(
  value: PayrollRunComparisonDto,
  id: string,
  baseline: string,
  after: string | null,
  changedOnly: boolean,
): PayrollRunComparison {
  check(
    value.current.id.toLowerCase() === id &&
      value.baseline.id.toLowerCase() === baseline &&
      id !== baseline,
  );
  check(
    value.baseline.earningsMonth < value.current.earningsMonth &&
      value.baseline.periodId.toLowerCase() !== value.current.periodId.toLowerCase() &&
      value.changedOnly === changedOnly,
  );
  const s = value.summary;
  check(
    s.baselineEmployees === value.baseline.employees &&
      s.currentEmployees === value.current.employees,
  );
  check(
    s.matched + s.removed === s.baselineEmployees &&
      s.matched + s.added === s.currentEmployees &&
      s.changed + s.unchanged === s.matched,
  );
  checkAmounts(s.baseline, s.baselineEmployees);
  checkAmounts(s.current, s.currentEmployees);
  checkDelta(s.baseline, s.current, s.delta);
  const items = value.employees.items.map((row) => {
    check(row.baseline !== null || row.current !== null);
    if (row.baseline) checkAmounts(row.baseline.amounts, 1);
    if (row.current) checkAmounts(row.current.amounts, 1);
    checkDelta(row.baseline?.amounts ?? null, row.current?.amounts ?? null, row.delta);
    const change =
      row.baseline === null
        ? "ADDED"
        : row.current === null
          ? "REMOVED"
          : keys.some((key) => minor(row.delta[key]) !== 0n)
            ? "CHANGED"
            : "UNCHANGED";
    check(change === row.change && (!changedOnly || change !== "UNCHANGED"));
    const freezeEmployee = (e: typeof row.baseline) =>
      e ? Object.freeze({ ...e, amounts: Object.freeze(e.amounts) }) : null;
    return Object.freeze({
      ...row,
      employmentId: row.employmentId.toLowerCase(),
      baseline: freezeEmployee(row.baseline),
      current: freezeEmployee(row.current),
      delta: Object.freeze(row.delta),
    });
  });
  check(
    items.length <=
      (changedOnly ? s.added + s.removed + s.changed : s.added + s.removed + s.matched),
  );
  check(
    items.every(
      (row, index) =>
        (after === null || row.employmentId > after) &&
        (index === 0 || row.employmentId > (items[index - 1]?.employmentId ?? "")),
    ),
  );
  if (after === null) {
    const total = changedOnly ? s.added + s.removed + s.changed : s.added + s.removed + s.matched;
    check(items.length === Math.min(total, 50));
    check((value.employees.nextCursor !== null) === total > 50);
  }
  const next = value.employees.nextCursor?.toLowerCase() ?? null;
  check(next === null || (items.length === 50 && next === items.at(-1)?.employmentId));
  const freezeRun = (run: typeof value.current) =>
    Object.freeze({ ...run, id: run.id.toLowerCase(), periodId: run.periodId.toLowerCase() });
  return Object.freeze({
    ...value,
    baseline: freezeRun(value.baseline),
    current: freezeRun(value.current),
    summary: Object.freeze({
      ...s,
      baseline: Object.freeze(s.baseline),
      current: Object.freeze(s.current),
      delta: Object.freeze(s.delta),
    }),
    employees: Object.freeze({ items: Object.freeze(items), nextCursor: next }),
  });
}
