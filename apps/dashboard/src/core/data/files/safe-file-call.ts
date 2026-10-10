import { failed, type Result, success } from "../../domain/result";
import { FileEncodingError, FileSizeLimitError } from "./file-errors";

/** File I/O boundary. A cancelled picker is a successful null; caller cancellation propagates. */
export async function safeFileCall<T>(
  signal: AbortSignal,
  operation: () => Promise<T>,
): Promise<Result<T>> {
  signal.throwIfAborted();
  try {
    const value = await operation();
    signal.throwIfAborted();
    return success(value);
  } catch (error) {
    signal.throwIfAborted();
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    if (error instanceof DOMException && error.name === "TimeoutError")
      return failed("file_read_timeout");
    if (error instanceof FileSizeLimitError) return failed("file_size_limit");
    if (error instanceof FileEncodingError) return failed("file_encoding_invalid");
    if (error instanceof DOMException && ["NotAllowedError", "SecurityError"].includes(error.name))
      return failed("file_access_denied");
    return failed("file_unavailable");
  }
}
