import { z } from "zod";

const count = z.number().int().min(0).max(5000);
const money = z.string().regex(/^-?(?:0|[1-9]\d{0,15})\.\d{2}$/u);
const amounts = z.strictObject({ taxableGross: money, withheld: money, takeHome: money });
const run = z.strictObject({
  id: z.uuid(),
  periodId: z.uuid(),
  number: z.number().int().min(1).max(20),
  earningsMonth: z.string().regex(/^(?:202[4-9]|20[3-9]\d|2100)-(?:0[1-9]|1[0-2])$/u),
  status: z.enum(["CALCULATED", "FINALIZED"]),
  version: z.number().int().min(0).max(25),
  employees: count.min(1),
});
const employee = z.strictObject({
  number: z.string().min(1).max(40),
  name: z.string().min(1).max(200),
  amounts,
});
export const payrollRunComparisonDto = z.strictObject({
  baseline: run,
  current: run,
  summary: z.strictObject({
    baselineEmployees: count,
    currentEmployees: count,
    matched: count,
    added: count,
    removed: count,
    changed: count,
    unchanged: count,
    baseline: amounts,
    current: amounts,
    delta: amounts,
  }),
  employees: z.strictObject({
    items: z
      .array(
        z.strictObject({
          employmentId: z.uuid(),
          baseline: employee.nullable(),
          current: employee.nullable(),
          change: z.enum(["ADDED", "REMOVED", "CHANGED", "UNCHANGED"]),
          delta: amounts,
        }),
      )
      .max(50),
    nextCursor: z.uuid().nullable(),
  }),
  changedOnly: z.boolean(),
  observedAt: z.iso.datetime(),
});
export type PayrollRunComparisonDto = z.infer<typeof payrollRunComparisonDto>;
