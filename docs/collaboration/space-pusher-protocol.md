# Organization Matrix pusher protocol

The existing fixed Matrix pusher adapter accepts the original `hriscp1_` and separate
organization `hrisgp1_` capability namespaces. Both retain application ID
`dev.fajar.hris.chat`, the configured callback endpoint, `event_id_only`, and `append`.
The provider receives no native FCM/APNs token. Malformed or foreign namespaces fail
before I/O; response bounds and failure/cancellation behavior are unchanged.

Company and group capabilities may coexist on one original Matrix device during
handover. Retry keeps the same capability and provider profile. Removing one key does
not remove the other. Actual Synapse tests verify both records across provider restart
and independent removal. Provider credential expiry does not erase retained pushers.

The deployable formatter now redacts full or partial capabilities from both namespaces
before enforcing its byte bound, including substituted arguments, exception chains,
cached exception text and stack fields. It never mutates the original record or falls
back to raw arguments after formatting failure. Actual provider logs and fresh-process
loading of the deployment configuration are tested. No live logging configuration is
changed by these repository updates.

Five bounded transport and three actual Synapse cases pass, along with fourteen Python
formatter cases for both namespaces. Format and architecture checks pass on unchanged
source fingerprints. This is protocol acceptance only: native group reservation and
publication, callback admission, group queue/worker and Mobile background/reply routing
remain required. Group rollout stays disabled and all 54 PRD groups remain open.

See [accepted gate evidence](../measurements/collaboration-space-pusher-protocol-2026-11-04.json).
