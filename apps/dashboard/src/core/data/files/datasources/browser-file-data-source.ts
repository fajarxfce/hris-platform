import { FileEncodingError, FileSizeLimitError } from "../file-errors";
import type { BinaryFileDownloadDto } from "../models/binary-file-dto";
import type { TextFileDownloadDto, TextFileDto } from "../models/text-file-dto";
import type { FileDataSource } from "./file-data-source";

export class BrowserFileDataSource implements FileDataSource {
  constructor(
    private readonly document: Document,
    private readonly createReader: () => FileReader = () => new FileReader(),
    private readonly readTimeoutMilliseconds = 15_000,
    private readonly objectUrls: Pick<typeof URL, "createObjectURL" | "revokeObjectURL"> = URL,
  ) {
    if (
      !Number.isInteger(readTimeoutMilliseconds) ||
      readTimeoutMilliseconds < 1 ||
      readTimeoutMilliseconds > 60_000
    )
      throw new RangeError("Invalid file read deadline");
  }

  select(accept: string, signal: AbortSignal): Promise<File | null> {
    signal.throwIfAborted();
    const input = this.document.createElement("input");
    input.type = "file";
    input.accept = accept;
    input.multiple = false;
    input.hidden = true;
    return new Promise((resolve, reject) => {
      const clean = () => {
        input.removeEventListener("change", selected);
        input.removeEventListener("cancel", cancelled);
        signal.removeEventListener("abort", aborted);
        input.remove();
      };
      const selected = () => {
        const file = input.files?.item(0) ?? null;
        clean();
        resolve(file);
      };
      const cancelled = () => {
        clean();
        resolve(null);
      };
      const aborted = () => {
        clean();
        reject(signal.reason);
      };
      input.addEventListener("change", selected, { once: true });
      input.addEventListener("cancel", cancelled, { once: true });
      signal.addEventListener("abort", aborted, { once: true });
      try {
        this.document.body.append(input);
        input.click();
      } catch (error) {
        clean();
        reject(error);
      }
    });
  }

  readText(file: File, maximumBytes: number, signal: AbortSignal): Promise<TextFileDto> {
    signal.throwIfAborted();
    if (!Number.isSafeInteger(maximumBytes) || maximumBytes < 1 || maximumBytes > 1024 * 1024)
      throw new RangeError("Invalid file acquisition limit");
    if (file.size > maximumBytes) throw new FileSizeLimitError();
    const reader = this.createReader();
    return new Promise((resolve, reject) => {
      const clean = () => {
        clearTimeout(deadline);
        reader.onload = null;
        reader.onerror = null;
        reader.onabort = null;
        signal.removeEventListener("abort", aborted);
      };
      const aborted = () => {
        clean();
        reader.abort();
        reject(signal.reason);
      };
      const deadline = setTimeout(() => {
        clean();
        reader.abort();
        reject(new DOMException("File read timed out", "TimeoutError"));
      }, this.readTimeoutMilliseconds);
      reader.onload = () => {
        clean();
        try {
          if (typeof reader.result === "string" || reader.result === null)
            throw new FileEncodingError();
          if (reader.result.byteLength > maximumBytes) throw new FileSizeLimitError();
          let text: string;
          try {
            text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(reader.result);
          } catch {
            throw new FileEncodingError();
          }
          resolve({ name: file.name, byteLength: reader.result.byteLength, text });
        } catch (error) {
          reject(error);
        }
      };
      reader.onerror = () => {
        clean();
        reject(reader.error ?? new DOMException("File read failed", "NotReadableError"));
      };
      reader.onabort = () => {
        clean();
        reject(new DOMException("File read aborted", "AbortError"));
      };
      signal.addEventListener("abort", aborted, { once: true });
      try {
        reader.readAsArrayBuffer(file);
      } catch (error) {
        clean();
        reject(error);
      }
    });
  }

  async downloadText(file: TextFileDownloadDto, signal: AbortSignal): Promise<void> {
    signal.throwIfAborted();
    if (new TextEncoder().encode(file.text).byteLength > 1024 * 1024)
      throw new FileSizeLimitError();
    const anchor = this.document.createElement("a");
    anchor.download = file.name;
    anchor.hidden = true;
    // A bounded data URL needs no object-URL registry or delayed revocation timer.
    anchor.href = `data:${file.mediaType};charset=utf-8,${encodeURIComponent(file.text)}`;
    try {
      this.document.body.append(anchor);
      signal.throwIfAborted();
      anchor.click();
    } finally {
      anchor.remove();
    }
  }

  async downloadBinary(file: BinaryFileDownloadDto, signal: AbortSignal): Promise<void> {
    signal.throwIfAborted();
    if (
      !Number.isSafeInteger(file.byteLength) ||
      file.byteLength < 1 ||
      file.byteLength > 100 * 1024 * 1024 ||
      file.parts.length < 1 ||
      file.parts.length > 1600 ||
      file.parts.some((part) => part.byteLength < 1 || part.byteLength > 64 * 1024) ||
      file.parts.reduce((size, part) => size + part.byteLength, 0) !== file.byteLength
    )
      throw new FileSizeLimitError();
    const anchor = this.document.createElement("a");
    const url = this.objectUrls.createObjectURL(
      new Blob([...file.parts], { type: file.mediaType }),
    );
    try {
      anchor.download = file.name;
      anchor.hidden = true;
      anchor.href = url;
      await new Promise<void>((resolve, reject) => {
        const clean = () => {
          clearTimeout(timer);
          signal.removeEventListener("abort", aborted);
        };
        const completed = () => {
          clean();
          resolve();
        };
        const aborted = () => {
          clean();
          reject(signal.reason);
        };
        // Retain the URL briefly while the browser accepts the native download, then release it.
        const timer = setTimeout(completed, 1000);
        signal.addEventListener("abort", aborted, { once: true });
        try {
          this.document.body.append(anchor);
          signal.throwIfAborted();
          anchor.click();
        } catch (error) {
          clean();
          reject(error);
        }
      });
    } finally {
      anchor.remove();
      this.objectUrls.revokeObjectURL(url);
    }
  }
}
