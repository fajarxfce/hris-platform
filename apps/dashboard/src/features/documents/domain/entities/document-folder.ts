import type { DocumentPage } from "./document";
import type { DocumentEmployee } from "./document-employee";

export type DocumentFolder = Readonly<{ employee: DocumentEmployee; documents: DocumentPage }>;
