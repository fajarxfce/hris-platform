import type { CompanyId } from "../../../../core/domain/identifiers";

export type HeadcountReport = Readonly<{
  companyId: CompanyId;
  asOf: string;
  evaluatedAt: string;
  definitionVersion: "headcount.v1";
  employments: number;
  persons: number;
  active: number;
  probation: number;
  suspended: number;
  permanent: number;
  fixedTerm: number;
}>;
