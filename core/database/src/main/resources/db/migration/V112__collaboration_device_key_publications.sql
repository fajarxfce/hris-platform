-- Reserve a device's public-key identity before any provider upload. A lost or
-- cancelled exchange may have written keys; a later request cannot replace that
-- identity. Only its first successful, fenced readback marks publication.
CREATE TABLE collaboration_device_key_publications (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    fingerprint varchar(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    version bigint NOT NULL CHECK(version IN (0,1)),
    reserved_at timestamptz NOT NULL CHECK(isfinite(reserved_at)),
    published_at timestamptz CHECK(published_at IS NULL OR (isfinite(published_at) AND published_at>=reserved_at)),
    PRIMARY KEY(company_id,device_id),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id),
    CHECK((version=1)=(published_at IS NOT NULL))
);
ALTER TABLE collaboration_device_key_publications ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_device_key_publications FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_key_publication_read ON collaboration_device_key_publications FOR SELECT
    USING(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY collaboration_key_publication_insert ON collaboration_device_key_publications FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY collaboration_key_publication_update ON collaboration_device_key_publications FOR UPDATE
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE FUNCTION protect_collaboration_device_key_publication() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Collaboration public key identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial public key reservation' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.fingerprint,NEW.reserved_at)
            IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.fingerprint,OLD.reserved_at)
            OR OLD.version<>0 OR NEW.version<>1 OR NEW.published_at IS NULL THEN
            RAISE EXCEPTION 'Invalid public key publication transition' USING ERRCODE='23514';
        END IF;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM collaboration_devices d
        WHERE d.company_id=NEW.company_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
          AND d.state='ACTIVE' AND d.revoked_at IS NULL AND d.created_at<=NEW.reserved_at) THEN
        RAISE EXCEPTION 'Invalid public key device scope' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_device_key_publication BEFORE INSERT OR UPDATE OR DELETE ON collaboration_device_key_publications
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_device_key_publication();
