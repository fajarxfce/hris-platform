export type BinaryFileSelection = Readonly<{ accept: string; maximumBytes: number }>;

/** An owned browser file reference, without SDK objects or file contents. Release it on departure. */
export type BinaryFileReference = Readonly<{
  id: string;
  name: string;
  mediaType: string;
  byteLength: number;
}>;

/** The acquiring use case owns and clears these bytes after the transport operation. */
export type BinaryFilePart = Readonly<{ bytes: ArrayBuffer; sha256: string }>;
