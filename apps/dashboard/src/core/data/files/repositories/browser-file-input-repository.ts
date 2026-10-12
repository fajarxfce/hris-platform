import type { BinaryFileSelection } from "../../../domain/files/binary-file-input";
import type { FileInputRepository } from "../../../domain/files/file-input-repository";
import type { FileInputDataSource } from "../datasources/file-input-data-source";
import { safeFileCall } from "../safe-file-call";

export class BrowserFileInputRepository implements FileInputRepository {
  constructor(private readonly source: FileInputDataSource) {}
  async select(options: BinaryFileSelection, signal: AbortSignal) {
    let acquired: string | null = null;
    try {
      const result = await safeFileCall(signal, async () => {
        const selected = await this.source.select(options.accept, options.maximumBytes, signal);
        if (selected === null) return null;
        acquired = selected.id;
        if (
          !selected.id ||
          selected.id.length > 128 ||
          !selected.name ||
          selected.name.length > 1024 ||
          selected.mediaType.length > 255 ||
          !Number.isSafeInteger(selected.byteLength) ||
          selected.byteLength < 1 ||
          selected.byteLength > options.maximumBytes
        )
          throw new TypeError("Invalid selected file metadata");
        return Object.freeze({
          id: selected.id,
          name: selected.name,
          mediaType: selected.mediaType,
          byteLength: selected.byteLength,
        });
      });
      if (result.ok) acquired = null;
      return result;
    } finally {
      if (acquired !== null) this.source.release(acquired);
    }
  }
  hash(id: string, signal: AbortSignal) {
    return safeFileCall(signal, async () => {
      const digest = await this.source.hash(id, signal);
      if (!/^[a-f0-9]{64}$/u.test(digest)) throw new TypeError("Invalid file digest");
      return digest;
    });
  }
  async readPart(id: string, offset: number, byteLength: number, signal: AbortSignal) {
    let acquired: ArrayBuffer | null = null;
    try {
      const result = await safeFileCall(signal, async () => {
        const part = await this.source.readPart(id, offset, byteLength, signal);
        if (!(part.bytes instanceof ArrayBuffer)) throw new TypeError("Invalid file bytes");
        acquired = part.bytes;
        if (part.bytes.byteLength !== byteLength || !/^[a-f0-9]{64}$/u.test(part.sha256))
          throw new TypeError("Invalid file part");
        return Object.freeze({ bytes: part.bytes, sha256: part.sha256 });
      });
      if (result.ok) acquired = null;
      return result;
    } finally {
      if (acquired !== null) new Uint8Array(acquired).fill(0);
    }
  }
  release(id: string): void {
    this.source.release(id);
  }
}
