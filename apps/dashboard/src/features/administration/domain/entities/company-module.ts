export const companyModules = [
  "PEOPLE",
  "WORKFORCE",
  "LEAVE",
  "EXPENSES",
  "PAYROLL",
  "DOCUMENTS",
  "COMMUNICATIONS",
  "REPORTING",
] as const;
export type CompanyModule = (typeof companyModules)[number];
