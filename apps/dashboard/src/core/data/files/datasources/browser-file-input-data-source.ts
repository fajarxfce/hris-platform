import { sha256 } from "@noble/hashes/sha2.js";
import { bytesToHex } from "@noble/hashes/utils.js";
import { createBrowserUuid } from "../../identifiers/create-browser-uuid";
import { FileSizeLimitError } from "../file-errors";
import { readBrowserFileBytes } from "../read-browser-file-bytes";
import { selectBrowserFile } from "../select-browser-file";
import type { FileInputDataSource } from "./file-input-data-source";

type FileHandle = { file: File; byteLength: number; lifetime: AbortController; reading: boolean };

/** Owns at most four browser file handles. Contents are read in finite slices, never retained here. */
export class BrowserFileInputDataSource implements FileInputDataSource {
  #files = new Map<string, FileHandle>();
  constructor(
    private readonly document: Document,
    private readonly createReader: () => FileReader = () => new FileReader(),
    private readonly nextIdentifier: () => string = createBrowserUuid,
    private readonly readTimeoutMilliseconds = 15_000,
    private readonly hashTimeoutMilliseconds = 60_000,
  ) {
    for (const timeout of [readTimeoutMilliseconds, hashTimeoutMilliseconds])
      if (!Number.isInteger(timeout) || timeout < 1 || timeout > 60_000)
        throw new RangeError("Invalid file deadline");
  }
  async select(accept: string, maximumBytes: number, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!Number.isSafeInteger(maximumBytes) || maximumBytes < 1 || maximumBytes > 100 * 1024 * 1024)
      throw new RangeError("Invalid file acquisition limit");
    if (this.#files.size >= 4)
      throw new DOMException("File handle capacity reached", "QuotaExceededError");
    const file = await selectBrowserFile(this.document, accept, signal);
    signal.throwIfAborted();
    if (file === null) return null;
    if (file.size < 1 || file.size > maximumBytes) throw new FileSizeLimitError();
    // Recheck after the picker wait; another owner may have acquired a handle meanwhile.
    if (this.#files.size >= 4)
      throw new DOMException("File handle capacity reached", "QuotaExceededError");
    const id = this.nextIdentifier();
    if (!id || this.#files.has(id)) throw new TypeError("Invalid file handle identity");
    this.#files.set(id, {
      file,
      byteLength: file.size,
      lifetime: new AbortController(),
      reading: false,
    });
    return { id, name: file.name, mediaType: file.type, byteLength: file.size };
  }
  async hash(id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    const handle = this.#files.get(id);
    if (!handle) throw new DOMException("File handle unavailable", "NotFoundError");
    if (handle.reading) throw new DOMException("File read already active", "InvalidStateError");
    const timeout = new AbortController();
    const pending = AbortSignal.any([signal, handle.lifetime.signal, timeout.signal]);
    const hash = sha256.create();
    const deadline = setTimeout(
      () => timeout.abort(new DOMException("File hash timed out", "TimeoutError")),
      this.hashTimeoutMilliseconds,
    );
    handle.reading = true;
    try {
      // At most 1,600 asynchronous reads; each iteration advances by a positive, bounded slice.
      for (let offset = 0; offset < handle.byteLength; offset += 64 * 1024) {
        pending.throwIfAborted();
        const length = Math.min(64 * 1024, handle.byteLength - offset);
        const bytes = await readBrowserFileBytes(
          handle.file.slice(offset, offset + length),
          length,
          this.createReader,
          this.readTimeoutMilliseconds,
          pending,
        );
        try {
          pending.throwIfAborted();
          hash.update(new Uint8Array(bytes));
        } finally {
          new Uint8Array(bytes).fill(0);
        }
      }
      pending.throwIfAborted();
      return bytesToHex(hash.digest());
    } finally {
      clearTimeout(deadline);
      hash.destroy();
      handle.reading = false;
    }
  }
  async readPart(id: string, offset: number, byteLength: number, signal: AbortSignal) {
    signal.throwIfAborted();
    const handle = this.#files.get(id);
    if (!handle) throw new DOMException("File handle unavailable", "NotFoundError");
    if (handle.reading) throw new DOMException("File read already active", "InvalidStateError");
    if (
      !Number.isSafeInteger(offset) ||
      offset < 0 ||
      !Number.isSafeInteger(byteLength) ||
      byteLength < 1 ||
      byteLength > 1024 * 1024 ||
      offset + byteLength > handle.byteLength
    )
      throw new FileSizeLimitError();
    const pending = AbortSignal.any([signal, handle.lifetime.signal]);
    handle.reading = true;
    let bytes: ArrayBuffer | null = null;
    try {
      bytes = await readBrowserFileBytes(
        handle.file.slice(offset, offset + byteLength),
        byteLength,
        this.createReader,
        this.readTimeoutMilliseconds,
        pending,
      );
      pending.throwIfAborted();
      return { bytes, sha256: bytesToHex(sha256(new Uint8Array(bytes))) };
    } catch (error) {
      if (bytes) new Uint8Array(bytes).fill(0);
      throw error;
    } finally {
      handle.reading = false;
    }
  }
  release(id: string): void {
    const handle = this.#files.get(id);
    this.#files.delete(id);
    handle?.lifetime.abort(new DOMException("File handle released", "AbortError"));
  }
}
