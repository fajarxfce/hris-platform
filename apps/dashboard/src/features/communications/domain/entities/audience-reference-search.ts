import type { AudienceReferenceKind } from "./audience-reference";

export type AudienceReferenceSearch = Readonly<{
  kind: AudienceReferenceKind;
  query: string;
  ids: readonly string[];
  after: string | null;
}>;
