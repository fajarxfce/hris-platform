import type { TextFile } from "../../../../core/domain/files/text-file";

export type EmployeeImportStart = Readonly<{ id: string; file: TextFile; reason: string }>;
