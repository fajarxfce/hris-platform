import { FileSizeLimitError } from "./file-errors";

/** Reads exactly one bounded Blob slice and owns its FileReader, deadline and abort listener. */
export function readBrowserFileBytes(
  blob: Blob,
  byteLength: number,
  createReader: () => FileReader,
  timeoutMilliseconds: number,
  signal: AbortSignal,
): Promise<ArrayBuffer> {
  signal.throwIfAborted();
  if (
    !Number.isSafeInteger(byteLength) ||
    byteLength < 1 ||
    byteLength > 1024 * 1024 ||
    blob.size !== byteLength
  )
    throw new FileSizeLimitError();
  if (
    !Number.isInteger(timeoutMilliseconds) ||
    timeoutMilliseconds < 1 ||
    timeoutMilliseconds > 60_000
  )
    throw new RangeError("Invalid file read deadline");
  const reader = createReader();
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
    }, timeoutMilliseconds);
    reader.onload = () => {
      clean();
      const bytes = reader.result;
      if (!(bytes instanceof ArrayBuffer)) {
        reject(new DOMException("File read failed", "NotReadableError"));
      } else if (bytes.byteLength !== byteLength || signal.aborted) {
        new Uint8Array(bytes).fill(0);
        reject(
          signal.aborted
            ? signal.reason
            : new DOMException("File changed during reading", "NotReadableError"),
        );
      } else resolve(bytes);
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
      reader.readAsArrayBuffer(blob);
    } catch (error) {
      clean();
      reject(error);
    }
  });
}
