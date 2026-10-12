import type { Failure } from "../../../../core/domain/result";

export type DocumentDownloadState = Readonly<{
  stage: "idle" | "downloading" | "started" | "failed" | "unavailable";
  failure: Failure | null;
}>;
export const initialDocumentDownloadState: DocumentDownloadState = Object.freeze({
  stage: "idle",
  failure: null,
});
