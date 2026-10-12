import { vi } from "vitest";
import type { FileInputRepository } from "../../src/core/domain/files/file-input-repository";

export function documentFileInput(): FileInputRepository {
  return {
    select: vi.fn<FileInputRepository["select"]>(),
    hash: vi.fn<FileInputRepository["hash"]>(),
    readPart: vi.fn<FileInputRepository["readPart"]>(),
    release: vi.fn<FileInputRepository["release"]>(),
  };
}
