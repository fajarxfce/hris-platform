import type { TextFileDownloadDto, TextFileDto } from "../models/text-file-dto";

export interface FileDataSource {
  select(accept: string, signal: AbortSignal): Promise<File | null>;
  readText(file: File, maximumBytes: number, signal: AbortSignal): Promise<TextFileDto>;
  downloadText(file: TextFileDownloadDto, signal: AbortSignal): Promise<void>;
}
