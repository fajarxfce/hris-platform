export type TextFile = Readonly<{ name: string; byteLength: number; text: string }>;
export type TextFileSelection = Readonly<{ accept: string; maximumBytes: number }>;
export type TextFileDownload = Readonly<{ name: string; mediaType: "text/csv"; text: string }>;
