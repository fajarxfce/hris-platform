# Native direct-room setup and reconciliation

The native conversation's `/{conversationId}/room` subresource supports POST setup
and GET status. Both require current HRIS native/company/CHAT authority, active
client policy and a positive Android/iOS build. They return no-store responses.
Provider credentials remain internal, and no Matrix content endpoint is opened.

POST requires an Idempotency-Key and uses `deviceId`, `expectedDeviceVersion`
and nullable `expectedProvisioningVersion`. Null requests initial setup; an existing
pointer returns its retained receipt without another job. A numeric version requests
explicit retry of a terminal failed/cancelled attempt. Only the original creator
may retry, at least sixty seconds after the previous request, using an active
registered device of that same account. Creator and canonical alias never change.
The selected peer's current active company/account/CHAT eligibility is checked even
on receipt replay. Native refresh may preserve a committed operation, but another
parent/device/request payload cannot reuse its key.

The operation, room and access guards precede native parent/device and the bounded
company job queue. Receipt, pointer, job, audit and outbox commit atomically. The
queue admits at most 100 pending jobs per company. Retry uses nonlocking terminal
job lookup, preserving worker lease-before-room ordering. Ordinary expiry does not
erase retained setup intent. The existing queue owns finite attempts, cancellation,
backoff and company/global concurrency; the API does not wait for a provider.

GET exposes conversation ID, setup version, NOT_REQUESTED/QUEUED/RUNNING/READY/
FAILED/CANCELLED, optional room ID/creator and retry eligibility/time. It does not
expose job payloads, native parent/device details, tokens or raw provider failures.
The room guard keeps pointer/binding reads coherent; current native assurance is
rechecked after acquisition, including error outcomes. Metadata remains available
to a current participant after the peer leaves. READY means verified room setup;
it does not authorize message content or imply the peer has encryption keys.

## Worker

The COLLABORATION_DIRECT_PROVISION handler uses the ordinary owned lease/batch
runtime. Preparation locks lease, room, policy/company/membership, ordered accounts,
native parent and device. It requires the current pointer, active initiating
device, current creator and peer eligibility, native assurance and client policy.
The first proof expires within sixty seconds or its earlier lease/authority limit.

Each provider exchange runs outside SQL and repeats current authority before and
after I/O, including failure responses. Changed observations are retryable; actual
revocation, MFA, maintenance or peer denial remains authoritative. A current private
provider credential may be reused; otherwise one bounded private login candidate
is obtained for the same creator/device. It does not replace public SDK credentials,
publish participant private keys or trigger logout. Unpublished credentials expire
privately within ten minutes.

The worker resolves the canonical alias, creates only if missing, and resolves
again after an explicit alias conflict. A lost create response leaves durable
intent for the next attempt. Before repair it validates immutable company/direct
marker, creator, version 12, disabled federation, member set, absence of additional
creators/tombstones, and security settings. It fills only missing initial settings
and a missing peer invitation, then inspects the complete state again. Foreign or
incompatible state is refused. The bounded pass has at most twelve HTTP exchanges
including login; the original proof/lease deadline also limits the pass. There is
no unbounded reconciliation loop or room/device deletion compensation.

Publication reacquires all guards and compares the original observation and
pointer. Binding, job progress/completion, audit and outbox commit together; a
failed result, late deadline or interruption rolls them back. An obsolete lease
cannot publish. A subsequent attempt inspects the same provider room instead of
creating another one. Explicit cancellation/terminal failure ends only the owned
job and retains identity for review/retry.

## Provider rate limits and remaining delivery work

All private broker logins may share one source address. Pinned Synapse 1.164.1
has a default rc_login.address bucket of five with 0.003 requests/second refill.
The expanded actual-worker suite reached HTTP 429; the existing bounded retry
classification remained intact. Its isolated provider now uses five requests/second
and a burst of 32 for that address bucket. Account and failed-attempt limits stay
at provider defaults. This is fixture configuration, not production capacity proof.
Rollout must size the private broker's provider limit together with HRIS admission,
credential renewal and measured load, while preserving finite rate limits.

The first offline-invitee experiments require `invited` history and an already
enrolled encryption device. A never-enrolled recipient still needs a pending
product outbox until eligible keys exist. Setup READY is not device delivery.
Protected SDK key/sync/content operations, browser-parent admission, native/web
product stores and UI remain required. Public direct Synapse login, room/state,
admin and federation paths remain denied. No real employee chat or phone action
is part of these tests.

[Validation evidence](../measurements/collaboration-direct-room-provisioning-2026-10-31.json)
retains exact source hashes, corrected failures and real provider/queue scenarios.
The full 5m12s selection passed 18 domain, 114 data, 145 selected API and 112 worker
cases (one unchanged opt-in retention skip), with formatting and architecture checks.
