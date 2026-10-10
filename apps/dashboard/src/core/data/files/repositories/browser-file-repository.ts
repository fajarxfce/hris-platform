import type { BinaryFileDownload } from "../../../domain/files/binary-file";
import type { FileRepository } from "../../../domain/files/file-repository";
import type { TextFileDownload, TextFileSelection } from "../../../domain/files/text-file";
import type { FileDataSource } from "../datasources/file-data-source";
import { safeFileCall } from "../safe-file-call";

export class BrowserFileRepository implements FileRepository {
  constructor(private readonly source: FileDataSource) {}
  downloadBinary(file: BinaryFileDownload, signal: AbortSignal) {
    return safeFileCall(signal, () =>
      this.source.downloadBinary(
        {
          name: file.name,
          mediaType: file.mediaType,
          byteLength: file.byteLength,
          parts: file.parts,
        },
        signal,
      ),
    );
  }
  selectText(options: TextFileSelection, signal: AbortSignal) {
    return safeFileCall(signal, async () => {
      const selected = await this.source.select(options.accept, signal);
      if (selected === null) return null;
      signal.throwIfAborted();
      const file = await this.source.readText(selected, options.maximumBytes, signal);
      return Object.freeze({ name: file.name, byteLength: file.byteLength, text: file.text });
    });
  }
  downloadText(file: TextFileDownload, signal: AbortSignal) {
    return safeFileCall(signal, () =>
      this.source.downloadText(
        { name: file.name, mediaType: file.mediaType, text: file.text },
        signal,
      ),
    );
  }
}
