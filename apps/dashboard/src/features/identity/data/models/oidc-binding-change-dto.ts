export type OidcBindingChangeDto = Readonly<{
  issuer: string;
  subject: string;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
