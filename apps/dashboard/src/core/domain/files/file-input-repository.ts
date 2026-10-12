import type { Result } from "../result";
import type { BinaryFilePart, BinaryFileReference, BinaryFileSelection } from "./binary-file-input";

export interface FileInputRepository {
  select(
    options: BinaryFileSelection,
    signal: AbortSignal,
  ): Promise<Result<BinaryFileReference | null>>;
  hash(id: string, signal: AbortSignal): Promise<Result<string>>;
  readPart(
    id: string,
    offset: number,
    byteLength: number,
    signal: AbortSignal,
  ): Promise<Result<BinaryFilePart>>;
  release(id: string): void;
}
