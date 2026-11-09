-- A worker acknowledges a target under that target's account/native/device guards.
-- The lease and publication still own the queue. Keep publisher-only release and
-- every existing channel, capability, immutable, temporal and cursor constraint.
-- Do not switch actor context inside the transaction or rewrite V139 history.
CREATE OR REPLACE FUNCTION protect_collaboration_space_readiness_delivery() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization readiness identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF public.current_company_id() IS NOT NULL OR NEW.publisher_account_id IS DISTINCT FROM public.current_actor_id()
            OR NEW.after_account_id IS NOT NULL OR NEW.lease_token IS NOT NULL OR NEW.attempts<>0
            OR NEW.completed_at IS NOT NULL OR NEW.available_at<>NEW.captured_at
            OR NEW.captured_at>clock_timestamp()
            OR NOT EXISTS(SELECT 1 FROM public.collaboration_space_readiness_revisions r
                WHERE r.space_id=NEW.space_id AND r.revision=NEW.publication_revision)
            OR NOT EXISTS(SELECT 1 FROM public.outbox_events e
                JOIN public.collaboration_space_key_publications k
                    ON k.device_id=e.resource_id AND k.account_id=e.actor_id
                WHERE e.id=NEW.event_id AND e.company_id IS NULL
                    AND e.event_type='collaboration.space_device_keys_published'
                    AND e.actor_id=NEW.publisher_account_id AND e.resource_id=NEW.publisher_device_id
                    AND k.space_id=NEW.space_id AND k.version=1 AND k.published_at=NEW.publication_cutoff) THEN
            RAISE EXCEPTION 'Invalid organization readiness source' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF public.current_company_id() IS NOT NULL
        OR NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Organization readiness worker required' USING ERRCODE='42501';
    END IF;
    IF (NEW.event_id,NEW.channel,NEW.space_id,NEW.publisher_account_id,NEW.publisher_device_id,
        NEW.publication_revision,NEW.publication_cutoff,NEW.captured_at,NEW.expires_at)
        IS DISTINCT FROM (OLD.event_id,OLD.channel,OLD.space_id,OLD.publisher_account_id,OLD.publisher_device_id,
        OLD.publication_revision,OLD.publication_cutoff,OLD.captured_at,OLD.expires_at)
        OR OLD.completed_at IS NOT NULL THEN
        RAISE EXCEPTION 'Organization readiness identity changed' USING ERRCODE='23514';
    END IF;
    IF (NEW.after_account_id,NEW.after_device_id) IS DISTINCT FROM (OLD.after_account_id,OLD.after_device_id) THEN
        IF (OLD.publisher_account_id IS DISTINCT FROM public.current_actor_id()
                AND NEW.after_account_id IS DISTINCT FROM public.current_actor_id())
            OR OLD.lease_token IS NULL OR OLD.lease_until<=clock_timestamp()
            OR (NEW.lease_owner,NEW.lease_token,NEW.lease_until)
                IS DISTINCT FROM (OLD.lease_owner,OLD.lease_token,OLD.lease_until)
            OR NEW.after_account_id IS NULL
            OR (OLD.after_account_id IS NOT NULL AND
                (NEW.after_account_id,NEW.after_device_id)<=(OLD.after_account_id,OLD.after_device_id))
            OR NEW.attempts<>0 OR NEW.available_at<>OLD.available_at OR NEW.completed_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid organization readiness cursor advance' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NOT NULL AND NEW.lease_token IS DISTINCT FROM OLD.lease_token THEN
        IF OLD.lease_until>clock_timestamp() OR OLD.expires_at<=clock_timestamp()
            OR OLD.available_at>clock_timestamp() OR OLD.attempts>=8
            OR NEW.attempts<>OLD.attempts+1 OR NEW.available_at<>OLD.available_at
            OR NEW.completed_at IS NOT NULL OR NEW.lease_until<=clock_timestamp()
            OR NEW.lease_until>clock_timestamp()+interval '60 seconds' THEN
            RAISE EXCEPTION 'Invalid organization readiness claim' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NULL AND OLD.lease_token IS NOT NULL THEN
        IF OLD.publisher_account_id IS DISTINCT FROM public.current_actor_id()
            OR OLD.lease_until<=clock_timestamp() OR NEW.attempts<>OLD.attempts
            OR NEW.available_at>clock_timestamp()+interval '300 seconds'
            OR (NEW.completed_at IS NOT NULL AND NEW.completed_at>clock_timestamp()) THEN
            RAISE EXCEPTION 'Invalid organization readiness release' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Organization readiness update requires a lease transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
