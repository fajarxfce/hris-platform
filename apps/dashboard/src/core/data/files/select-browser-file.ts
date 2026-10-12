/** Raw native picker I/O, shared by text and binary adapters. No file eligibility policy. */
export function selectBrowserFile(
  document: Document,
  accept: string,
  signal: AbortSignal,
): Promise<File | null> {
  signal.throwIfAborted();
  const input = document.createElement("input");
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
      document.body.append(input);
      input.click();
    } catch (error) {
      clean();
      reject(error);
    }
  });
}
