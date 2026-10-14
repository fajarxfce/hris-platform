import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollRunEmployee } from "../../domain/entities/payroll-run-employee";
import type { PayrollRunEmployeeDto } from "../models/payroll-calculation-dto";
import { toPayrollRunItem } from "./payroll-calculation-mapper";
import { toPayrollCalculationOutput } from "./payroll-calculation-output-mapper";
export function payrollCalculationMinor(value: string): bigint {
  const negative = value.startsWith("-"),
    [whole = "0", fraction = ""] = value.replace(/^-/, "").split(".");
  return (BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"))) * (negative ? -1n : 1n);
}
export function toPayrollRunEmployee(
  raw: PayrollRunEmployeeDto,
  company: CompanyId,
  runId: string,
  employee: string,
): PayrollRunEmployee {
  const item = toPayrollRunItem(raw.item),
    facts = raw.facts,
    output = raw.calculation;
  if (
    item.target.employeeId !== employee ||
    (item.failure === null && (!facts || !output)) ||
    (item.failure !== null && output !== null)
  )
    throw new InvalidHttpResponseError();
  if (
    output &&
    ["taxableGross", "withheld", "takeHome"].some(
      (key) =>
        payrollCalculationMinor(output.tax[key as "taxableGross" | "withheld" | "takeHome"]) !==
        payrollCalculationMinor(item[key as "taxableGross" | "withheld" | "takeHome"] ?? "0"),
    )
  )
    throw new InvalidHttpResponseError();
  if (
    facts &&
    (facts.incomeDueDate.slice(0, 7) !== facts.month ||
      facts.policy.effectiveFrom > facts.month ||
      facts.policy.effectiveUntil < facts.month ||
      new Set(facts.workDays.map((day) => day.date)).size !== facts.workDays.length ||
      facts.workDays.some((day) => day.date.slice(0, 7) !== facts.month) ||
      facts.leaveDays.some((day) => day.date.slice(0, 7) !== facts.month))
  )
    throw new InvalidHttpResponseError();
  const evidence = facts
    ? Object.freeze({
        month: facts.month,
        incomeDueDate: facts.incomeDueDate,
        plannedPaymentDate: facts.plannedPaymentDate,
        policy: Object.freeze({ ...facts.policy }),
        basicSalary: facts.compensation.basicSalary,
        inputReference: facts.input.reviewReference,
        openingThroughMonth: facts.taxHistory.throughMonth,
        openingReference: facts.taxHistory.reference,
        workDays: Object.freeze(facts.workDays.map((day) => Object.freeze({ ...day }))),
        leaveDays: Object.freeze(
          facts.leaveDays.map((day) =>
            Object.freeze({ ...day, requestId: day.requestId.toLowerCase() }),
          ),
        ),
      })
    : null;
  const calculation = output ? toPayrollCalculationOutput(output) : null;
  return Object.freeze({ companyId: company, runId, item, evidence, calculation });
}
