/** Only the exact fragment emitted by the trusted mail template is accepted. */
export function credentialTokenFromFragment(fragment: string): string | null {
  return /^#token=([A-Za-z0-9_-]{43})$/u.exec(fragment)?.[1] ?? null;
}
