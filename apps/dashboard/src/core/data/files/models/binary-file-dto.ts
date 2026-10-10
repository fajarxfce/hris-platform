export type BinaryFileDownloadDto = Readonly<{
  name: string;
  mediaType: string;
  parts: readonly ArrayBuffer[];
  byteLength: number;
}>;
