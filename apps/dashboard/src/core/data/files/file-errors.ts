export class FileSizeLimitError extends Error {
  constructor() {
    super("File exceeds the acquisition limit");
    this.name = "FileSizeLimitError";
  }
}
export class FileEncodingError extends Error {
  constructor() {
    super("File is not valid UTF-8");
    this.name = "FileEncodingError";
  }
}
