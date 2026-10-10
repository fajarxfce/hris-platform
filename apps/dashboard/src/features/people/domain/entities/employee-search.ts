export type EmployeeSearch = Readonly<{
  asOf: string;
  query: string;
  after: string | null;
}>;
