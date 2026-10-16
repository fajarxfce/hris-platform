import type { OidcBindingDto } from "../../src/features/identity/data/models/oidc-binding-dto";

export const oidcIssuer = "https://oidc.example.test";
export function oidcBindingId(index = 1): string {
  return `33000000-0000-4000-8000-${index.toString().padStart(12, "0")}`;
}
export function oidcBinding(index = 1): OidcBindingDto {
  return {
    id: oidcBindingId(index),
    issuer: oidcIssuer,
    subject: `Owned-Subject-${index}`,
    active: true,
    version: 3,
    linkedAt: "2026-10-01T09:00:00Z",
  };
}
