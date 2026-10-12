export type BinaryFileReferenceDto = Readonly<{
  id: string;
  name: string;
  mediaType: string;
  byteLength: number;
}>;
export type BinaryFilePartDto = Readonly<{ bytes: ArrayBuffer; sha256: string }>;
