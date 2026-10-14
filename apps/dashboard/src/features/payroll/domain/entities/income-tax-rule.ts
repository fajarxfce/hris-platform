export type IncomeTaxRule = Readonly<{
  id: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  sources: readonly string[];
}>;
