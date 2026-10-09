import type { CompanyHeadcount } from "./company-headcount";
import type { HeadcountCounts } from "./headcount-counts";

export type HeadcountReport = Readonly<{
  asOf: string;
  evaluatedAt: string;
  definitionVersion: "headcount.v1";
  totals: HeadcountCounts;
  companies: readonly CompanyHeadcount[];
}>;
