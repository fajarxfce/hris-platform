import type { CompanyId } from "../../../../core/domain/identifiers";
import type { HeadcountCounts } from "./headcount-counts";

export type CompanyHeadcount = Readonly<{ companyId: CompanyId; counts: HeadcountCounts }>;
