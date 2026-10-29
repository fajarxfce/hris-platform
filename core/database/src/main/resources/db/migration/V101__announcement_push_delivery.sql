-- Keep raw recipient/session dispatch metadata private. This projection carries
-- only per-inbox operational counters, grouped by announcement by the API.
LOCK TABLE inbox_push_dispatches IN SHARE ROW EXCLUSIVE MODE;
CREATE TABLE announcement_push_delivery (
    company_id uuid NOT NULL,
    announcement_id uuid NOT NULL,
    inbox_id uuid NOT NULL,
    state varchar(16) NOT NULL CHECK(state IN ('PENDING','LEASED','COMPLETE','FAILED','SUPERSEDED')),
    processed_count integer NOT NULL CHECK(processed_count BETWEEN 0 AND 500),
    accepted_count integer NOT NULL CHECK(accepted_count BETWEEN 0 AND 500),
    rejected_count integer NOT NULL CHECK(rejected_count BETWEEN 0 AND 500),
    PRIMARY KEY(company_id,inbox_id),
    FOREIGN KEY(company_id,inbox_id) REFERENCES inbox_items(company_id,id),
    FOREIGN KEY(company_id,announcement_id) REFERENCES announcement_heads(company_id,id),
    CHECK(accepted_count+rejected_count<=processed_count)
);
CREATE INDEX announcement_push_delivery_summary ON announcement_push_delivery(company_id,announcement_id,inbox_id);
INSERT INTO announcement_push_delivery
SELECT d.company_id,i.announcement_id,d.inbox_id,d.state,d.processed_count,d.accepted_count,d.rejected_count
FROM inbox_push_dispatches d JOIN inbox_items i ON i.company_id=d.company_id AND i.id=d.inbox_id;

ALTER TABLE announcement_push_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcement_push_delivery FORCE ROW LEVEL SECURITY;
CREATE POLICY announcement_push_delivery_read ON announcement_push_delivery FOR SELECT USING (
    company_id=current_company_id() AND EXISTS (
        SELECT 1 FROM accounts a JOIN company_memberships m ON m.account_id=a.id
        JOIN companies c ON c.id=m.company_id
        JOIN membership_permissions p ON p.company_id=m.company_id AND p.account_id=m.account_id
        WHERE a.id=current_actor_id() AND a.active AND m.active AND c.active
            AND m.company_id=current_company_id() AND p.permission='announcements.manage'
    )
);
-- Only the table owner's fixed trigger can maintain this projection. Application
-- and worker runtime roles are not table owners and gain no direct write policy.
CREATE POLICY announcement_push_delivery_capture ON announcement_push_delivery FOR ALL USING (
    current_user = pg_catalog.pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid='public.announcement_push_delivery'::regclass))
) WITH CHECK (
    current_user = pg_catalog.pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid='public.announcement_push_delivery'::regclass))
);

-- Trigger-only entry point: reads no raw recipient/session data and returns no
-- query result. The exact source table, fixed names and search path prevent use
-- as a general definer query or mutation capability. Direct execution is revoked.
CREATE FUNCTION capture_announcement_push_delivery() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE announcement uuid;
BEGIN
    IF TG_TABLE_SCHEMA<>'public' OR TG_TABLE_NAME<>'inbox_push_dispatches' THEN
        RAISE EXCEPTION 'Unexpected delivery projection source' USING ERRCODE='42501';
    END IF;
    IF TG_OP='DELETE' THEN
        DELETE FROM public.announcement_push_delivery WHERE company_id=OLD.company_id AND inbox_id=OLD.inbox_id;
        RETURN OLD;
    END IF;
    IF TG_OP='INSERT' THEN
        SELECT announcement_id INTO STRICT announcement FROM public.inbox_items
            WHERE company_id=NEW.company_id AND id=NEW.inbox_id;
        INSERT INTO public.announcement_push_delivery(company_id,announcement_id,inbox_id,state,processed_count,accepted_count,rejected_count)
        VALUES(NEW.company_id,announcement,NEW.inbox_id,NEW.state,NEW.processed_count,NEW.accepted_count,NEW.rejected_count);
    ELSIF TG_OP='UPDATE' THEN
        UPDATE public.announcement_push_delivery SET state=NEW.state,processed_count=NEW.processed_count,
            accepted_count=NEW.accepted_count,rejected_count=NEW.rejected_count
        WHERE company_id=NEW.company_id AND inbox_id=NEW.inbox_id;
        IF NOT FOUND THEN RAISE EXCEPTION 'Delivery projection is missing' USING ERRCODE='23514'; END IF;
    ELSE
        RAISE EXCEPTION 'Unexpected delivery projection operation' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION capture_announcement_push_delivery() FROM PUBLIC;
CREATE TRIGGER capture_announcement_push_delivery AFTER INSERT OR UPDATE OR DELETE ON inbox_push_dispatches
FOR EACH ROW EXECUTE FUNCTION capture_announcement_push_delivery();
