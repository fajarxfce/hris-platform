import { BrowserFileInputDataSource } from "../data/files/datasources/browser-file-input-data-source";
import { BrowserFileInputRepository } from "../data/files/repositories/browser-file-input-repository";

export function createBrowserFileInput(document: Document) {
  return new BrowserFileInputRepository(new BrowserFileInputDataSource(document));
}
