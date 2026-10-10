export type EmploymentTerms = Readonly<{
  effectiveFrom: string;
  contract: "PERMANENT" | "FIXED_TERM";
  startDate: string;
  endDate: string | null;
  status: "PROBATION" | "ACTIVE" | "SUSPENDED" | "ENDED";
}>;
