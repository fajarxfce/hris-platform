import type { z } from "zod";
import type { AudienceGroupChangeDto } from "../models/audience-group-change-dto";
import type { AudienceGroupDto, AudienceGroupPageDto } from "../models/audience-group-dto";
import type { communicationsReceiptDto } from "../models/communications-receipt-dto";

export interface AudienceGroupDataSource {
  list(company: string, after: string | null, signal: AbortSignal): Promise<AudienceGroupPageDto>;
  get(
    company: string,
    id: string,
    revision: number | null,
    signal: AbortSignal,
  ): Promise<AudienceGroupDto>;
  save(
    company: string,
    id: string,
    operation: string,
    input: AudienceGroupChangeDto,
    signal: AbortSignal,
  ): Promise<z.infer<typeof communicationsReceiptDto>>;
}
