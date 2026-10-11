export type AudienceReferenceKind = "BRANCH" | "DEPARTMENT" | "GROUP" | "EMPLOYMENT";
export type AudienceReference = Readonly<{
  id: string;
  kind: AudienceReferenceKind;
  name: string;
  code: string | null;
  version: number;
  active: boolean | null;
}>;
export type AudienceReferencePage = Readonly<{
  items: readonly AudienceReference[];
  nextCursor: string | null;
}>;
