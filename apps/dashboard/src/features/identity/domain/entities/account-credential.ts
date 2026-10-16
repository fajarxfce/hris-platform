export type AccountCredentialKind = "invitation" | "passwordRecovery";
export type AccountCredential = Readonly<{ token: string; password: string }>;
