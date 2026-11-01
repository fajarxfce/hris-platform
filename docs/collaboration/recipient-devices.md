# Public recipient-device projection

Schema 113 adds `collaboration_recipient_devices`, a read-only security-barrier
view for the current actor/company. It returns only company, account, device,
retained public-key fingerprint and an aggregate `receive_until` deadline. Schema 114
adds `publication_confirmed` and includes committed immutable reservations before
the publisher records its provider readback. The
underlying device registry, session binding and key-publication tables keep
their existing private application-role RLS. No label, native session ID, login
credential, MFA timestamp or participant private key appears in the projection.

The view owner is a separate non-login, non-inheriting, non-superuser role with
no RLS bypass, schema creation, database creation or role creation authority.
Its column grants cover only the inputs to this projection. Three SELECT-only
policies let this role read the selected company's private device/binding/key
facts. Runtime roles are not members of it. The view does not grant any mutation
capability, and callers cannot choose a different view owner or source query.

Both actor and recipient must have active accounts and company memberships with
`collaboration.chat`; the company must be active. Recipients are the actor's own
devices or an existing direct-conversation peer. Company-wide contact discovery
does not expose everyone else's device keys. Current product admission, client
policy, MFA and conversation authorization still belong to the use case. This
projection is not a login credential or a substitute for those checks.

A candidate device must be active with an immutable key reservation. Every key
returned by the provider still needs its canonical self signature and retained
fingerprint checked; a reservation alone never supplies trusted key bytes. A
missing provider key is allowed only before publication confirmation. A confirmed
key disappearing fails the query. Room-message admission still requires at least
one confirmed recipient. Key claims and encrypted to-device submissions also
retain their confirmed-publication requirement and independently verify keys.
At least one bound native parent must be unrevoked, within its hard lifetime,
and match the account's current credential/security version. Expired short
access tokens, normal token rotation and old MFA verification do not remove an
otherwise eligible offline recipient. Multiple valid parents yield one device
with the latest valid hard deadline; revoked parents cannot extend it. Parent
cleanup removes eligibility without deleting the retained device or key identity.
This affects future delivery, not owned local history.

The storage port accepts at most 100 account IDs, takes a detached input snapshot,
and requests at most 501 ordered rows. It returns at most 500 or fails closed on
overflow. An application clock cutoff additionally fences the SQL transaction's
time snapshot. No SQL transaction spans provider I/O. Device-set completeness,
per-account limits, exact Matrix user/device mapping, provider fingerprint checks
and pre/post-exchange admission remain the consuming key-query use case's work.
An empty eligible list must not become a self-only message reported as delivered.

The [native recipient-key query](device-key-query.md) now binds this repository
and owns its per-account bounds, device-set completeness and pre/post-exchange
checks. The view by itself grants no provider operation. Group/resource-context
membership and browser-parent enrollment will extend their own eligibility
contracts; this initial view authorizes only existing direct pairs and self.
These schemas add no secret envelope; recovery's reviewed ceiling is 114 and
115 remains rejected. Preserve the view's restricted owner and role grants when
restoring a backup.

[Evidence](../measurements/collaboration-recipient-devices-2026-10-31.json)
records seven actual PostgreSQL cases, schema-112 upgrade preservation and
pg_dump/restore of the restricted owner/RLS behavior. Full relevant database,
domain, data, API and worker checks pass; the existing opt-in worker retention
case remains skipped. No production deployment or client rollout occurred.

Schema 114 closes the publication race described in [recipient key query](device-key-query.md):
the provider can announce its new device key while the publisher's HRIS readback
transaction is still pending. Both reservation and confirmation retain identical
scope, hard-parent eligibility and pin validation. The existing evidence below
remains the schema-113 baseline; the foreground/native measurement records the
schema-114 upgrade, restore and race checks separately.
