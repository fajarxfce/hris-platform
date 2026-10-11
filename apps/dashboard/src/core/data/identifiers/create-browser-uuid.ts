type IdentifierCrypto = Pick<Crypto, "getRandomValues"> & Partial<Pick<Crypto, "randomUUID">>;

/** Uses browser entropy on HTTPS and on the private HTTP development origin. */
export function createBrowserUuid(source: IdentifierCrypto = globalThis.crypto): string {
  if (source.randomUUID) return source.randomUUID();
  const bytes = source.getRandomValues(new Uint8Array(16));
  const hex = Array.from(bytes, (value, index) => {
    const byte = index === 6 ? (value & 0x0f) | 0x40 : index === 8 ? (value & 0x3f) | 0x80 : value;
    return byte.toString(16).padStart(2, "0");
  }).join("");
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join("-");
}
