import { createHash } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BrowserFileInputDataSource } from "./datasources/browser-file-input-data-source";
import type { FileInputDataSource } from "./datasources/file-input-data-source";
import { BrowserFileInputRepository } from "./repositories/browser-file-input-repository";

const options = { accept: ".pdf,.png,.jpg", maximumBytes: 100 * 1024 * 1024 };
const signal = () => new AbortController().signal;
const hash = (bytes: Uint8Array) => createHash("sha256").update(bytes).digest("hex");
const file = () =>
  new File([Uint8Array.from([1, 2, 3])], "record.pdf", { type: "application/pdf" });
function choose(value: File | null) {
  return vi.spyOn(HTMLInputElement.prototype, "click").mockImplementation(function (
    this: HTMLInputElement,
  ) {
    Object.defineProperty(this, "files", { value: { item: () => value }, configurable: true });
    this.dispatchEvent(new Event(value === null ? "cancel" : "change"));
  });
}
function sourceFixture() {
  return {
    select: vi.fn<FileInputDataSource["select"]>().mockResolvedValue({
      id: "file-1",
      name: "record.pdf",
      mediaType: "application/pdf",
      byteLength: 3,
    }),
    hash: vi.fn<FileInputDataSource["hash"]>(),
    readPart: vi.fn<FileInputDataSource["readPart"]>(),
    release: vi.fn<FileInputDataSource["release"]>(),
  };
}
afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
  document.body.replaceChildren();
});

describe("owned binary file input", () => {
  it("keeps picker cancellation separate from abort and acquires immutable metadata only", async () => {
    const click = choose(null),
      source = new BrowserFileInputDataSource(document);
    const repository = new BrowserFileInputRepository(source);
    expect(await repository.select(options, signal())).toEqual({ ok: true, value: null });
    expect(document.querySelector('input[type="file"]')).toBeNull();
    click.mockRestore();
    choose(file());
    const result = await repository.select(options, signal());
    expect(result).toMatchObject({
      ok: true,
      value: { name: "record.pdf", mediaType: "application/pdf", byteLength: 3 },
    });
    if (!result.ok || !result.value) throw new Error("Expected an owned file");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.keys(result.value).sort()).toEqual(["byteLength", "id", "mediaType", "name"]);
    repository.release(result.value.id);
    repository.release(result.value.id);
    expect(await repository.hash(result.value.id, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_unavailable" },
    });
  });

  it("hashes in bounded asynchronous slices and clears every acquisition buffer", async () => {
    const bytes = Uint8Array.from({ length: 3 * 64 * 1024 + 11 }, (_, index) => index % 251);
    choose(new File([bytes], "record.pdf", { type: "application/pdf" }));
    const readers: FileReader[] = [],
      lengths: number[] = [];
    const source = new BrowserFileInputDataSource(document, () => {
      const reader = new FileReader(),
        read = reader.readAsArrayBuffer.bind(reader);
      vi.spyOn(reader, "readAsArrayBuffer").mockImplementation((blob) => {
        lengths.push(blob.size);
        read(blob);
      });
      readers.push(reader);
      return reader;
    });
    const selected = await source.select(options.accept, options.maximumBytes, signal());
    if (!selected) throw new Error("Expected an owned file");
    expect(await source.hash(selected.id, signal())).toBe(hash(bytes));
    expect(lengths).toEqual([65536, 65536, 65536, 11]);
    for (const reader of readers) {
      expect(new Uint8Array(reader.result as ArrayBuffer).every((byte) => byte === 0)).toBe(true);
      expect([reader.onload, reader.onerror, reader.onabort]).toEqual([null, null, null]);
    }
    source.release(selected.id);
  });

  it("reads only the requested slice with its checksum and bounds reads before SDK I/O", async () => {
    const bytes = Uint8Array.from({ length: 8192 }, (_, index) => index % 251);
    choose(new File([bytes], "photo.png", { type: "image/png" }));
    const reader = vi.fn(() => new FileReader()),
      source = new BrowserFileInputDataSource(document, reader);
    const selected = await source.select(options.accept, options.maximumBytes, signal());
    if (!selected) throw new Error("Expected an owned file");
    const part = await source.readPart(selected.id, 1021, 4096, signal());
    expect(new Uint8Array(part.bytes)).toEqual(bytes.slice(1021, 5117));
    expect(part.sha256).toBe(hash(bytes.slice(1021, 5117)));
    new Uint8Array(part.bytes).fill(0);
    for (const [offset, length] of [
      [-1, 2],
      [0, 0],
      [8190, 3],
      [0, 1024 * 1024 + 1],
      [0.1, 2],
    ])
      await expect(
        source.readPart(selected.id, offset ?? -1, length ?? 0, signal()),
      ).rejects.toMatchObject({ name: "FileSizeLimitError" });
    expect(reader).toHaveBeenCalledTimes(1);
    source.release(selected.id);
  });

  it("bounds concurrent ownership after pending pickers complete and releases capacity", async () => {
    let identifier = 0;
    const source = new BrowserFileInputDataSource(
      document,
      () => new FileReader(),
      () => `file-${identifier++}`,
    );
    const picker = choose(file()),
      retained: string[] = [];
    for (let index = 0; index < 3; index += 1) {
      const selected = await source.select(options.accept, options.maximumBytes, signal());
      if (selected) retained.push(selected.id);
    }
    picker.mockRestore();
    const inputs: HTMLInputElement[] = [];
    const waitingPicker = vi
      .spyOn(HTMLInputElement.prototype, "click")
      .mockImplementation(function (this: HTMLInputElement) {
        inputs.push(this);
      });
    const first = source.select(options.accept, options.maximumBytes, signal());
    const second = source.select(options.accept, options.maximumBytes, signal());
    const rejected = expect(second).rejects.toMatchObject({ name: "QuotaExceededError" });
    for (const input of inputs) {
      Object.defineProperty(input, "files", { value: { item: file } });
      input.dispatchEvent(new Event("change"));
    }
    const fourth = await first;
    if (fourth) retained.push(fourth.id);
    await rejected;
    expect(document.querySelectorAll('input[type="file"]')).toHaveLength(0);
    expect(await new BrowserFileInputRepository(source).select(options, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_capacity_exceeded" },
    });
    for (const id of retained) source.release(id);
    expect(inputs).toHaveLength(2);
    waitingPicker.mockRestore();
    choose(file());
    const recovered = await source.select(options.accept, options.maximumBytes, signal());
    expect(recovered).not.toBeNull();
    if (recovered) source.release(recovered.id);
  });

  it("rejects empty and oversized files before retaining them", async () => {
    const click = choose(new File([], "empty.pdf")),
      source = new BrowserFileInputDataSource(document);
    const repository = new BrowserFileInputRepository(source);
    expect(await repository.select(options, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_size_limit" },
    });
    click.mockRestore();
    choose(file());
    expect(await repository.select({ ...options, maximumBytes: 2 }, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_size_limit" },
    });
  });

  it("releasing a file aborts its active hash, rejects concurrent reads and clears timers", async () => {
    choose(file());
    const reader = new FileReader();
    const read = vi.spyOn(reader, "readAsArrayBuffer").mockImplementation(() => {});
    const abort = vi.spyOn(reader, "abort");
    const source = new BrowserFileInputDataSource(document, () => reader);
    const selected = await source.select(options.accept, options.maximumBytes, signal());
    if (!selected) throw new Error("Expected an owned file");
    vi.useFakeTimers();
    const running = source.hash(selected.id, signal());
    const rejected = expect(running).rejects.toMatchObject({ name: "AbortError" });
    expect(
      await new BrowserFileInputRepository(source).readPart(selected.id, 0, 1, signal()),
    ).toMatchObject({ ok: false, failure: { code: "file_in_use" } });
    source.release(selected.id);
    await rejected;
    expect(abort).toHaveBeenCalledTimes(1);
    expect(read).toHaveBeenCalledTimes(1);
    expect([reader.onload, reader.onerror, reader.onabort]).toEqual([null, null, null]);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("enforces the whole-file deadline without extending it after each read", async () => {
    choose(file());
    const reader = new FileReader();
    vi.spyOn(reader, "readAsArrayBuffer").mockImplementation(() => {});
    const source = new BrowserFileInputDataSource(
      document,
      () => reader,
      () => "file-1",
      1000,
      10,
    );
    const repository = new BrowserFileInputRepository(source);
    const selected = await source.select(options.accept, options.maximumBytes, signal());
    if (!selected) throw new Error("Expected an owned file");
    vi.useFakeTimers();
    const running = repository.hash(selected.id, signal());
    await vi.advanceTimersByTimeAsync(10);
    expect(await running).toMatchObject({ ok: false, failure: { code: "file_read_timeout" } });
    expect(vi.getTimerCount()).toBe(0);
    source.release(selected.id);
  });

  it("aborts a pending picker and removes its listeners without retaining a file", async () => {
    vi.spyOn(HTMLInputElement.prototype, "click").mockImplementation(() => {});
    const source = new BrowserFileInputDataSource(document),
      pending = new AbortController();
    const selecting = source.select(options.accept, options.maximumBytes, pending.signal);
    expect(document.querySelectorAll('input[type="file"]')).toHaveLength(1);
    pending.abort();
    await expect(selecting).rejects.toMatchObject({ name: "AbortError" });
    expect(document.querySelectorAll('input[type="file"]')).toHaveLength(0);
  });

  it.each(["metadata", "cancellation"] as const)(
    "releases a newly acquired handle after %s failure",
    async (kind) => {
      const source = sourceFixture(),
        repository = new BrowserFileInputRepository(source),
        pending = new AbortController();
      let resolve!: (value: Awaited<ReturnType<FileInputDataSource["select"]>>) => void;
      source.select.mockReturnValueOnce(
        new Promise((done) => {
          resolve = done;
        }),
      );
      const selected = repository.select(options, pending.signal);
      if (kind === "cancellation") pending.abort();
      resolve({
        id: "file-1",
        name: kind === "metadata" ? "" : "record.pdf",
        mediaType: "application/pdf",
        byteLength: 3,
      });
      if (kind === "cancellation")
        await expect(selected).rejects.toMatchObject({ name: "AbortError" });
      else
        expect(await selected).toMatchObject({ ok: false, failure: { code: "file_unavailable" } });
      expect(source.release).toHaveBeenCalledExactlyOnceWith("file-1");
    },
  );

  it.each(["mapping", "cancellation"] as const)(
    "clears an acquired part after %s failure",
    async (kind) => {
      const source = sourceFixture(),
        repository = new BrowserFileInputRepository(source),
        pending = new AbortController();
      const bytes = Uint8Array.from([1, 2, 3]);
      let resolve!: (value: Awaited<ReturnType<FileInputDataSource["readPart"]>>) => void;
      source.readPart.mockReturnValueOnce(
        new Promise((done) => {
          resolve = done;
        }),
      );
      const part = repository.readPart("file-1", 0, 3, pending.signal);
      if (kind === "cancellation") pending.abort();
      resolve({ bytes: bytes.buffer, sha256: kind === "mapping" ? "invalid" : hash(bytes) });
      if (kind === "cancellation") await expect(part).rejects.toMatchObject({ name: "AbortError" });
      else expect(await part).toMatchObject({ ok: false, failure: { code: "file_unavailable" } });
      expect(bytes.every((byte) => byte === 0)).toBe(true);
    },
  );
});
