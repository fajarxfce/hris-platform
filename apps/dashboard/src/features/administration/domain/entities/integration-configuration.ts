export const integrationKinds = [
  "FIREBASE_AUTH",
  "OIDC",
  "MAIL",
  "PUSH",
  "STORAGE",
  "DOCUMENT_SCANNER",
] as const;
export type IntegrationKind = (typeof integrationKinds)[number];
export type IntegrationConfiguration = Readonly<{
  runtime: "API";
  observedAt: string;
  integrations: readonly Readonly<{ kind: IntegrationKind; enabled: boolean }>[];
}>;
