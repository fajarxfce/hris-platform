-- Freeze feature-owned maker exclusions once, so reassignment and delegation cannot bypass them.
ALTER TABLE approval_requests ADD COLUMN excluded_account_ids uuid[] NOT NULL DEFAULT '{}';
ALTER TABLE approval_requests ADD CHECK(cardinality(excluded_account_ids)<=200 AND array_position(excluded_account_ids,NULL) IS NULL);
-- Existing expense submissions already retain this evidence; do not infer historical identities.
UPDATE approval_requests a SET excluded_account_ids=s.maker_ids
 FROM expense_submissions s WHERE a.company_id=s.company_id AND a.id=s.approval_id;
CREATE OR REPLACE FUNCTION protect_approval_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF (to_jsonb(NEW)-ARRAY['current_step','status','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['current_step','status','version'])
  THEN RAISE EXCEPTION 'Approval snapshots are immutable' USING ERRCODE='42501'; END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION check_approval_exclusion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE excluded uuid[]; BEGIN
 IF TG_TABLE_NAME='approval_requests' THEN
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(NEW.stages) stage,LATERAL jsonb_array_elements_text(stage->'assignees') account
    WHERE account::uuid=ANY(NEW.excluded_account_ids))
   THEN RAISE EXCEPTION 'Excluded makers cannot be approval assignees' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT excluded_account_ids INTO excluded FROM approval_requests WHERE company_id=NEW.company_id AND id=NEW.request_id;
  IF TG_TABLE_NAME='approval_decisions' THEN
   IF NEW.actor_id=ANY(excluded) OR NEW.deciding_for=ANY(excluded)
    THEN RAISE EXCEPTION 'Excluded makers cannot decide approvals' USING ERRCODE='23514'; END IF;
  ELSIF NEW.assignees && excluded THEN
   RAISE EXCEPTION 'Excluded makers cannot be reassigned approvals' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER approval_maker_scope BEFORE INSERT ON approval_requests FOR EACH ROW EXECUTE FUNCTION check_approval_exclusion();
CREATE TRIGGER approval_decision_maker_scope BEFORE INSERT ON approval_decisions FOR EACH ROW EXECUTE FUNCTION check_approval_exclusion();
CREATE TRIGGER approval_assignment_maker_scope BEFORE INSERT ON approval_assignment_overrides FOR EACH ROW EXECUTE FUNCTION check_approval_exclusion();
