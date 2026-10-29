import type { PayrollRunComparisonDto } from "../../src/features/payroll/data/models/payroll-run-comparison-dto";
import { calculationId, calculationPeriod } from "./payroll-calculations";
export const comparisonBaseline = "ec000000-0000-4000-8000-000000000009",
  comparisonPeriod = "ea800000-0000-4000-8000-000000000009";
const money = (value: bigint) =>
  `${value < 0n ? "-" : ""}${(value < 0n ? -value : value) / 100n}.${((value < 0n ? -value : value) % 100n).toString().padStart(2, "0")}`;
const keys = ["taxableGross", "withheld", "takeHome"] as const;
const amounts = (gross: string, withheld: string, home: string) => ({
  taxableGross: gross,
  withheld,
  takeHome: home,
});
const minor = (value: string) => BigInt(value.replace(".", ""));
export function comparisonFixture(): PayrollRunComparisonDto {
  const employee = (number: string, name: string, value: ReturnType<typeof amounts>) => ({
    number,
    name,
    amounts: value,
  });
  const unchanged = employee("EMP-002", "Unchanged employee", amounts("100.00", "0.00", "100.00"));
  const rows: PayrollRunComparisonDto["employees"]["items"] = [
    {
      employmentId: "ec300000-0000-4000-8000-000000000001",
      baseline: employee(
        "EMP-001",
        "Original name",
        amounts("10000000.01", "-0.01", "10000000.02"),
      ),
      current: employee("EMP-001", "Updated name", amounts("10000000.02", "0.01", "10000000.01")),
      change: "CHANGED",
      delta: amounts("0.01", "0.02", "-0.01"),
    },
    {
      employmentId: "ec300000-0000-4000-8000-000000000002",
      baseline: unchanged,
      current: unchanged,
      change: "UNCHANGED",
      delta: amounts("0.00", "0.00", "0.00"),
    },
    {
      employmentId: "ec300000-0000-4000-8000-000000000003",
      baseline: employee("EMP-003", "Departed employee", amounts("200.00", "20.00", "180.00")),
      current: null,
      change: "REMOVED",
      delta: amounts("-200.00", "-20.00", "-180.00"),
    },
    {
      employmentId: "ec300000-0000-4000-8000-000000000004",
      baseline: null,
      current: employee("EMP-004", "New employee", amounts("300.00", "30.00", "270.00")),
      change: "ADDED",
      delta: amounts("300.00", "30.00", "270.00"),
    },
  ];
  const sum = (side: "baseline" | "current" | "delta") =>
    Object.fromEntries(
      keys.map((key) => [
        key,
        money(
          rows.reduce(
            (total, row) =>
              total + minor((side === "delta" ? row.delta : row[side]?.amounts)?.[key] ?? "0.00"),
            0n,
          ),
        ),
      ]),
    ) as ReturnType<typeof amounts>;
  return {
    baseline: {
      id: comparisonBaseline,
      periodId: comparisonPeriod,
      number: 1,
      earningsMonth: "2026-08",
      status: "CALCULATED",
      version: 1,
      employees: 3,
    },
    current: {
      id: calculationId,
      periodId: calculationPeriod,
      number: 1,
      earningsMonth: "2026-09",
      status: "CALCULATED",
      version: 1,
      employees: 3,
    },
    summary: {
      baselineEmployees: 3,
      currentEmployees: 3,
      matched: 2,
      added: 1,
      removed: 1,
      changed: 1,
      unchanged: 1,
      baseline: sum("baseline"),
      current: sum("current"),
      delta: sum("delta"),
    },
    employees: { items: rows, nextCursor: null },
    changedOnly: false,
    observedAt: "2026-10-29T02:00:00Z",
  };
}

/** Fifty-one matching employees exercise the actual client page boundary. */
export function comparisonManyFixture(): PayrollRunComparisonDto {
  const value = comparisonFixture();
  value.baseline.employees = 51;
  value.current.employees = 51;
  value.summary = {
    baselineEmployees: 51,
    currentEmployees: 51,
    matched: 51,
    added: 0,
    removed: 0,
    changed: 51,
    unchanged: 0,
    baseline: { taxableGross: "5100.00", withheld: "510.00", takeHome: "4590.00" },
    current: { taxableGross: "5151.00", withheld: "561.00", takeHome: "4590.00" },
    delta: { taxableGross: "51.00", withheld: "51.00", takeHome: "0.00" },
  };
  value.employees.items = Array.from({ length: 51 }, (_, index) => {
    const number = String(index + 1).padStart(3, "0");
    return {
      employmentId: `cf000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
      baseline: {
        name: `Employee ${number}`,
        number,
        amounts: { taxableGross: "100.00", withheld: "10.00", takeHome: "90.00" },
      },
      current: {
        name: `Employee ${number}`,
        number,
        amounts: { taxableGross: "101.00", withheld: "11.00", takeHome: "90.00" },
      },
      change: "CHANGED",
      delta: { taxableGross: "1.00", withheld: "1.00", takeHome: "0.00" },
    };
  });
  return value;
}
