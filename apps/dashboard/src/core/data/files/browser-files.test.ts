import { afterEach, describe, expect, it, vi } from "vitest";
import { BrowserFileDataSource } from "./datasources/browser-file-data-source";
import type { FileDataSource } from "./datasources/file-data-source";
import { FileEncodingError, FileSizeLimitError } from "./file-errors";
import { BrowserFileRepository } from "./repositories/browser-file-repository";
import { safeFileCall } from "./safe-file-call";

const signal = () => new AbortController().signal;
const selectedInput = () => {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error("Expected owned picker");
  return input;
};
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  document.body.replaceChildren();
});
describe("browser file resource ownership", () => {
  it("bounds file reading time and releases the reader on timeout", async () => {
    vi.useFakeTimers();
    const reader = new FileReader();
    vi.spyOn(reader, "readAsArrayBuffer").mockImplementation(() => {});
    const aborted = vi.spyOn(reader, "abort");
    const source = new BrowserFileDataSource(document, () => reader, 20);
    const owner = new AbortController();
    const pending = safeFileCall(owner.signal, () =>
      source.readText(new File(["name"], "input.csv"), 64, owner.signal),
    );
    await vi.advanceTimersByTimeAsync(20);
    expect(await pending).toMatchObject({ ok: false, failure: { code: "file_read_timeout" } });
    expect(aborted).toHaveBeenCalledOnce();
    expect(reader.onload).toBeNull();
    expect(reader.onerror).toBeNull();
    expect(reader.onabort).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("removes its picker and listeners after selection or native cancellation", async () => {
    const source = new BrowserFileDataSource(document);
    const owner = new AbortController();
    const removed = vi.spyOn(owner.signal, "removeEventListener");
    const selected = source.select(".csv,text/csv", owner.signal);
    const input = selectedInput();
    expect(input.accept).toBe(".csv,text/csv");
    expect(input.multiple).toBe(false);
    const file = new File(["name\nExample"], "employees.csv", { type: "text/csv" });
    Object.defineProperty(input, "files", { value: { item: () => file } });
    input.dispatchEvent(new Event("change"));
    expect(await selected).toBe(file);
    expect(input.isConnected).toBe(false);
    expect(removed).toHaveBeenCalledWith("abort", expect.any(Function));
    const cancelled = source.select(".csv", signal());
    selectedInput().dispatchEvent(new Event("cancel"));
    expect(await cancelled).toBeNull();
    expect(document.querySelector('input[type="file"]')).toBeNull();
  });
  it("aborts a pending picker and ignores a late native selection", async () => {
    const owner = new AbortController();
    const source = new BrowserFileDataSource(document);
    const pending = source.select(".csv", owner.signal);
    const input = selectedInput();
    const outcome = expect(pending).rejects.toMatchObject({ name: "AbortError" });
    owner.abort();
    await outcome;
    input.dispatchEvent(new Event("change"));
    expect(input.isConnected).toBe(false);
    expect(() => source.select(".csv", owner.signal)).toThrow();
  });
  it("cleans up a synchronously rejected picker", async () => {
    vi.spyOn(HTMLInputElement.prototype, "click").mockImplementation(() => {
      throw new DOMException("Private OS text", "NotAllowedError");
    });
    const owner = new AbortController();
    const removed = vi.spyOn(owner.signal, "removeEventListener");
    const result = await safeFileCall(owner.signal, () =>
      new BrowserFileDataSource(document).select(".csv", owner.signal),
    );
    expect(result).toMatchObject({ ok: false, failure: { code: "file_access_denied" } });
    expect(JSON.stringify(result)).not.toContain("Private");
    expect(removed).toHaveBeenCalled();
    expect(document.querySelector("input")).toBeNull();
  });
  it("reads bounded UTF-8 without stripping the BOM or changing bytes", async () => {
    const source = new BrowserFileDataSource(document);
    const text = '\ufeffname\r\n"Nurul, Fajar"\r\n日本';
    const file = new File([text], "employees.csv");
    expect(await source.readText(file, 100, signal())).toEqual({
      name: "employees.csv",
      text,
      byteLength: new TextEncoder().encode(text).byteLength,
    });
    expect(await source.readText(new File([], "empty.csv"), 100, signal())).toEqual({
      name: "empty.csv",
      text: "",
      byteLength: 0,
    });
  });
  it("rejects excessive size before creating a reader and reports invalid UTF-8 explicitly", async () => {
    const createReader = vi.fn(() => new FileReader());
    const source = new BrowserFileDataSource(document, createReader);
    expect(() => source.readText(new File(["x".repeat(65)], "large.csv"), 64, signal())).toThrow(
      FileSizeLimitError,
    );
    expect(createReader).not.toHaveBeenCalled();
    await expect(
      source.readText(new File([new Uint8Array([0xc3, 0x28])], "invalid.csv"), 64, signal()),
    ).rejects.toBeInstanceOf(FileEncodingError);
  });
  it("aborts a pending read and releases its callbacks and listener", async () => {
    const owner = new AbortController();
    const reader = new FileReader();
    reader.addEventListener("loadstart", () => owner.abort(), { once: true });
    const removed = vi.spyOn(owner.signal, "removeEventListener");
    const aborted = vi.spyOn(reader, "abort");
    const source = new BrowserFileDataSource(document, () => reader);
    await expect(
      source.readText(new File(["Pending"], "pending.csv"), 64, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(aborted).toHaveBeenCalledOnce();
    expect(removed).toHaveBeenCalled();
    expect(reader.onload).toBeNull();
    expect(reader.onerror).toBeNull();
    expect(reader.onabort).toBeNull();
  });
  it("cleans up downloads without retained object URLs and respects cancelled owners", async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe("template.csv");
      expect(this.href).toBe("data:text/csv;charset=utf-8,name%0AExample");
      expect(this.isConnected).toBe(true);
    });
    const source = new BrowserFileDataSource(document);
    const file = { name: "template.csv", mediaType: "text/csv" as const, text: "name\nExample" };
    await source.downloadText(file, signal());
    expect(document.querySelector("a")).toBeNull();
    const owner = new AbortController();
    owner.abort();
    await expect(source.downloadText(file, owner.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(click).toHaveBeenCalledOnce();
    click.mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    await expect(source.downloadText(file, signal())).rejects.toMatchObject({
      name: "SecurityError",
    });
    expect(document.querySelector("a")).toBeNull();
  });
});
describe("file repository boundaries", () => {
  it("coordinates selection and acquisition, preserving cancellation between them", async () => {
    const owner = new AbortController();
    const file = new File(["name"], "employees.csv");
    const select = vi
      .fn<FileDataSource["select"]>()
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(file)
      .mockImplementationOnce(async () => {
        owner.abort();
        return file;
      });
    const readText = vi
      .fn<FileDataSource["readText"]>()
      .mockResolvedValue({ name: file.name, text: "name", byteLength: 4 });
    const repo = new BrowserFileRepository({
      select,
      readText,
      downloadText: vi.fn(),
      downloadBinary: vi.fn(),
    });
    const options = { accept: ".csv", maximumBytes: 64 };
    expect(await repo.selectText(options, signal())).toEqual({ ok: true, value: null });
    expect(readText).not.toHaveBeenCalled();
    const result = await repo.selectText(options, signal());
    expect(result).toEqual({ ok: true, value: { name: file.name, text: "name", byteLength: 4 } });
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
    await expect(repo.selectText(options, owner.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(readText).toHaveBeenCalledOnce();
  });
  it.each([
    [new FileSizeLimitError(), "file_size_limit"],
    [new FileEncodingError(), "file_encoding_invalid"],
    [new DOMException("Private file path", "NotReadableError"), "file_unavailable"],
    [new DOMException("Private file path", "SecurityError"), "file_access_denied"],
  ])("maps a technical file failure without exposing its text", async (error, code) => {
    const result = await safeFileCall(signal(), async () => {
      throw error;
    });
    expect(result).toMatchObject({ ok: false, failure: { code } });
    expect(JSON.stringify(result)).not.toContain("Private");
  });
});
