export const maximumBinaryFileBytes = 100 * 1024 * 1024;
export const binaryFilePartBytes = 64 * 1024;

/** Owned acquisition buffers. Keep them inside the download operation, never in UI state or caches. */
export type BinaryFileContent = Readonly<{
  parts: readonly ArrayBuffer[];
  byteLength: number;
}>;
export type BinaryFileDownload = BinaryFileContent & Readonly<{ name: string; mediaType: string }>;
