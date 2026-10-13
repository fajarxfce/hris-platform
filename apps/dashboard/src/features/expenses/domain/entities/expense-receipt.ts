export type ExpenseReceipt = Readonly<{
  revisionId: string;
  fileName: string;
  mediaType: "application/pdf" | "image/jpeg" | "image/png";
  size: number;
  sha256: string;
  possibleDuplicate: boolean;
}>;
