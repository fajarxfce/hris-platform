import type { FileInputRepository } from "../../../../core/domain/files/file-input-repository";

/** Page departure releases local ownership independently of current server authorization. */
export class ReleaseDocumentUploadFile {
  constructor(private readonly files: FileInputRepository) {}
  execute(reference: string): void {
    this.files.release(reference);
  }
}
