import type {
  AudienceReferencePageDto,
  AudienceReferenceQueryDto,
} from "../models/audience-reference-dto";
export interface AudienceReferenceDataSource {
  list(
    company: string,
    search: AudienceReferenceQueryDto,
    signal: AbortSignal,
  ): Promise<AudienceReferencePageDto>;
}
