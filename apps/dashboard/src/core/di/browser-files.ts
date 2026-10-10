import { BrowserFileDataSource } from "../data/files/datasources/browser-file-data-source";
import { BrowserFileRepository } from "../data/files/repositories/browser-file-repository";

export function createBrowserFiles(document: Document = globalThis.document) {
  return new BrowserFileRepository(new BrowserFileDataSource(document));
}
