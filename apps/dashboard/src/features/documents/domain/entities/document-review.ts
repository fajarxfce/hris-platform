import type { Document } from "./document";
import type { DocumentRevisionPage } from "./document-revision";

export type DocumentReview = Readonly<{ document: Document; revisions: DocumentRevisionPage }>;
