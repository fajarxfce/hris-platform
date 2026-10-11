import { describe, expect, it, vi } from "vitest";
import { createBrowserUuid } from "./create-browser-uuid";

describe("browser identifiers", () => {
  it("uses the native UUID method when it is available", () => {
    const randomUUID = vi.fn(() => "51000000-0000-4000-8000-000000000001" as const);
    const getRandomValues = vi.fn(() => {
      throw new Error("Unexpected fallback");
    });
    expect(createBrowserUuid({ randomUUID, getRandomValues })).toBe(
      "51000000-0000-4000-8000-000000000001",
    );
    expect(randomUUID).toHaveBeenCalledTimes(1);
    expect(getRandomValues).not.toHaveBeenCalled();
  });
  it.each([
    [0, "00000000-0000-4000-8000-000000000000"],
    [255, "ffffffff-ffff-4fff-bfff-ffffffffffff"],
  ] as const)(
    "retains browser entropy and RFC 9562 version/variant bits without randomUUID",
    (fill, expected) => {
      let calls = 0;
      const source: Pick<Crypto, "getRandomValues"> = {
        getRandomValues(array) {
          calls++;
          if (array === null) throw new Error("Missing entropy buffer");
          expect(array.byteLength).toBe(16);
          new Uint8Array(array.buffer, array.byteOffset, array.byteLength).fill(fill);
          return array;
        },
      };
      expect(createBrowserUuid(source)).toBe(expected);
      expect(calls).toBe(1);
    },
  );
  it("propagates an entropy failure without falling back to an insecure generator", () => {
    expect(() =>
      createBrowserUuid({
        getRandomValues() {
          throw new DOMException("Unavailable", "NotSupportedError");
        },
      }),
    ).toThrow("Unavailable");
  });
});
