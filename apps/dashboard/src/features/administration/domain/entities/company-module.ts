export const companyModules = [
  "PEOPLE",
  "WORKFORCE",
  "LEAVE",
  "EXPENSES",
  "PAYROLL",
  "DOCUMENTS",
  "COMMUNICATIONS",
  "COLLABORATION",
  "REPORTING",
] as const;
export type CompanyModule = (typeof companyModules)[number];
