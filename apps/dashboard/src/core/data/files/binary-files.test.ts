import { afterEach, describe, expect, it, vi } from "vitest";
import { BrowserFileDataSource } from "./datasources/browser-file-data-source";
import { BrowserFileRepository } from "./repositories/browser-file-repository";
import { safeFileCall } from "./safe-file-call";

const file = () => ({
  name: "evidence.pdf",
  mediaType: "application/pdf",
  byteLength: 4,
  parts: [new Uint8Array([1, 2, 3, 4]).buffer],
});
function fixture() {
  const urls = {
    createObjectURL: vi.fn((_blob: Blob) => "blob:owned-download"),
    revokeObjectURL: vi.fn(),
  };
  const source = new BrowserFileDataSource(document, () => new FileReader(), 15_000, urls);
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  return { urls, source, click };
}
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  document.body.replaceChildren();
});
describe("native binary download ownership", () => {
  it("releases the anchor, temporary URL and handoff timer after native dispatch", async () => {
    vi.useFakeTimers();
    const f = fixture();
    const owner = new AbortController();
    const remove = vi.spyOn(owner.signal, "removeEventListener");
    const pending = new BrowserFileRepository(f.source).downloadBinary(file(), owner.signal);
    expect(f.click).toHaveBeenCalledOnce();
    expect(document.querySelector("a")?.download).toBe("evidence.pdf");
    expect(f.urls.createObjectURL.mock.calls[0]?.[0].size).toBe(4);
    expect(f.urls.createObjectURL.mock.calls[0]?.[0].type).toBe("application/pdf");
    expect(f.urls.revokeObjectURL).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1000);
    expect(await pending).toEqual({ ok: true, value: undefined });
    expect(f.urls.revokeObjectURL).toHaveBeenCalledWith("blob:owned-download");
    expect(document.querySelector("a")).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    expect(remove).toHaveBeenCalledWith("abort", expect.any(Function));
  });
  it("cancellation during handoff frees native resources and propagates the original abort", async () => {
    vi.useFakeTimers();
    const f = fixture();
    const owner = new AbortController();
    const pending = f.source.downloadBinary(file(), owner.signal);
    const outcome = expect(pending).rejects.toMatchObject({ name: "AbortError" });
    owner.abort();
    await outcome;
    expect(f.urls.revokeObjectURL).toHaveBeenCalledOnce();
    expect(document.querySelector("a")).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    await expect(f.source.downloadBinary(file(), owner.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(f.urls.createObjectURL).toHaveBeenCalledOnce();
  });
  it("failed native dispatch cleans up and returns a safe file failure", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.click.mockImplementation(() => {
      throw new DOMException("PRIVATE OS DETAILS", "NotAllowedError");
    });
    const result = await new BrowserFileRepository(f.source).downloadBinary(
      file(),
      new AbortController().signal,
    );
    expect(result).toMatchObject({ ok: false, failure: { code: "file_access_denied" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(document.querySelector("a")).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    expect(f.urls.revokeObjectURL).toHaveBeenCalledOnce();
  });
  it("validates the complete acquisition size and block count before allocating a native URL", async () => {
    const f = fixture();
    const owner = new AbortController();
    for (const invalid of [
      { ...file(), byteLength: 3 },
      { ...file(), byteLength: 100 * 1024 * 1024 + 1 },
      { ...file(), parts: [] },
      { ...file(), parts: [new ArrayBuffer(65537)], byteLength: 65537 },
      {
        ...file(),
        parts: Array.from({ length: 1601 }, () => new ArrayBuffer(1)),
        byteLength: 1601,
      },
    ])
      expect(
        await safeFileCall(owner.signal, () => f.source.downloadBinary(invalid, owner.signal)),
      ).toMatchObject({ ok: false, failure: { code: "file_size_limit" } });
    expect(f.urls.createObjectURL).not.toHaveBeenCalled();
    expect(f.click).not.toHaveBeenCalled();
  });
});
