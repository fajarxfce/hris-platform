import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AnnouncementReview } from "../../domain/entities/announcement-review";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";

export function useAnnouncementPreview(
  account: AccountId,
  access: CompanyAccess,
  review: AnnouncementReview | null,
  load: CommunicationsUseCases["previewAudience"],
) {
  const [requested, request] = useReducer(() => true, false);
  const available = review?.availableActions.includes("PREVIEW") ?? false;
  const query = useQuery({
    queryKey: [
      "announcement-audience-preview",
      account,
      access.companyId,
      access.permissions,
      review?.announcement.id,
      review?.announcement.version,
      review?.evaluatedAt,
    ],
    queryFn: ({ signal }) =>
      review
        ? load.execute(access, review.announcement, signal)
        : Promise.resolve(failed("announcement_preview_unavailable")),
    enabled: requested && available,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result =
    !available || query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    preview: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    loading: requested && available && (query.isPending || query.isFetching),
    available,
    refresh: () => {
      if (!available) return;
      if (!requested) request();
      else void query.refetch();
    },
  };
}
