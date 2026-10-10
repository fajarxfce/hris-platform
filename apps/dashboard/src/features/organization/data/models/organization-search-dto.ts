export type OrganizationSearchDto = Readonly<{
  query: string;
  kind: string | null;
  active: boolean | null;
  after: string | null;
}>;
