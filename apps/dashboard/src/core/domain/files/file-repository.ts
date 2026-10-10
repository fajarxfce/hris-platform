import type { Result } from "../result";
import type { TextFile, TextFileDownload, TextFileSelection } from "./text-file";

export interface FileRepository {
  selectText(options: TextFileSelection, signal: AbortSignal): Promise<Result<TextFile | null>>;
  downloadText(file: TextFileDownload, signal: AbortSignal): Promise<Result<void>>;
}
