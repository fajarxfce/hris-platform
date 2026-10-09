import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { HeadcountReport } from "../../domain/entities/headcount-report";
import type { HeadcountReportDto } from "../models/headcount-report-dto";
import { toHeadcountCounts } from "./headcount-counts-mapper";

export function toHeadcountReport(
  dto: HeadcountReportDto,
  selected: readonly CompanyId[],
  asOf: string,
): HeadcountReport {
  const ids = new Set(dto.companies.map((company) => company.companyId));
  if (
    dto.asOf !== asOf ||
    ids.size !== dto.companies.length ||
    ids.size !== selected.length ||
    selected.some((id) => !ids.has(id))
  ) {
    throw new InvalidHttpResponseError();
  }
  const totals = toHeadcountCounts(dto.totals);
  const companies = dto.companies
    .map((company) =>
      Object.freeze({
        companyId: company.companyId as CompanyId,
        counts: toHeadcountCounts(company.counts),
      }),
    )
    .sort((left, right) =>
      left.companyId < right.companyId ? -1 : left.companyId > right.companyId ? 1 : 0,
    );
  for (const metric of [
    "employments",
    "active",
    "probation",
    "suspended",
    "permanent",
    "fixedTerm",
  ] as const) {
    const sum = companies.reduce((value, company) => value + company.counts[metric], 0);
    if (!Number.isSafeInteger(sum) || sum !== totals[metric]) throw new InvalidHttpResponseError();
  }
  const persons = companies.map((company) => company.counts.persons);
  const sum = persons.reduce((value, count) => value + count, 0);
  if (!Number.isSafeInteger(sum) || totals.persons < Math.max(...persons) || totals.persons > sum)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    asOf,
    evaluatedAt: dto.evaluatedAt,
    definitionVersion: dto.definitionVersion,
    totals,
    companies: Object.freeze(companies),
  });
}
