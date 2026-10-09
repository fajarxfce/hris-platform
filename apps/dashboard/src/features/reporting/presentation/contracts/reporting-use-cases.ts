import type { LoadHeadcountReport } from "../../domain/usecases/load-headcount-report";

export type ReportingUseCases = Readonly<{ loadHeadcount: Pick<LoadHeadcountReport, "execute"> }>;
