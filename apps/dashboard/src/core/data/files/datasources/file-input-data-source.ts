import type { BinaryFilePartDto, BinaryFileReferenceDto } from "../models/binary-file-input-dto";

export interface FileInputDataSource {
  select(
    accept: string,
    maximumBytes: number,
    signal: AbortSignal,
  ): Promise<BinaryFileReferenceDto | null>;
  hash(id: string, signal: AbortSignal): Promise<string>;
  readPart(
    id: string,
    offset: number,
    byteLength: number,
    signal: AbortSignal,
  ): Promise<BinaryFilePartDto>;
  release(id: string): void;
}
